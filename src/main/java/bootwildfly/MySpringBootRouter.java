/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bootwildfly;

import com.arjuna.ats.jta.TransactionManager;
import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.activemq.camel.component.ActiveMQConfiguration;
import org.apache.activemq.pool.ActiveMQResourceManager;
import org.apache.activemq.pool.JcaPooledConnectionFactory;
import org.apache.camel.Processor;
import org.apache.camel.spring.boot.FatJarRouter;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication
public class MySpringBootRouter extends FatJarRouter {

//    @Autowired
//    private HealthEndpoint health;

    @Value("${source.broker.url}")
    String sourceBrokerUrl;
    
    @Value("${source.broker.username}")
    String sourceBrokerUsername;

    @Value("${source.broker.password}")
    String sourceBrokerPassword;

    @Value("${target.broker.url}")
    String targetBrokerUrl;

    @Value("${target.broker.username}")
    String targetBrokerUsername;

    @Value("${target.broker.password}")
    String targetBrokerPassword;



    @Override
    public void configure() {
//        from("timer:trigger?period=30000")
//                .transform().simple("ref:myBean")
//                .to("amqSource:queue:input");

        from("amqSource:queue:input")
                .transacted("springTransactionPolicy")
                .log("Received msg with JMSRedelivered:${header.JMSRedelivered}")
                .process(errorGenerator())
                .to("amqTarget:queue:output");

    }

    @Bean
    Processor errorGenerator(){
        TestProcessor tp = new TestProcessor();
        tp.setSimulateProcessingError(true);
        return tp;
    }


    @Bean
    String myBean() {
        return "I'm Spring bean!";
    }


    @Autowired
    PlatformTransactionManager platformTransactionManager;
//    @Bean

    @Bean
    RedeliveryPolicy standardRedeliveryPolicy(){
        RedeliveryPolicy rp = new RedeliveryPolicy();
        rp.setInitialRedeliveryDelay(1000);
        rp.setRedeliveryDelay(5000);
        rp.setMaximumRedeliveries(2);
        return rp;
    }

    @Bean
    SpringTransactionPolicy springTransactionPolicy(){
        SpringTransactionPolicy stp = new SpringTransactionPolicy();
        stp.setTransactionManager(platformTransactionManager);
        stp.setPropagationBehaviorName("PROPAGATION_REQUIRED");
        return stp;
    }

    @Bean
    ActiveMQXAConnectionFactory amqSourceConnectionFactory(){
        ActiveMQXAConnectionFactory amqSourceConFac = new ActiveMQXAConnectionFactory();
        amqSourceConFac.setBrokerURL(sourceBrokerUrl);
        amqSourceConFac.setUserName(sourceBrokerUsername);
        amqSourceConFac.setPassword(sourceBrokerPassword);
        amqSourceConFac.setRedeliveryPolicy(standardRedeliveryPolicy());
        return amqSourceConFac;
    }

    @Primary
    @Bean
    JcaPooledConnectionFactory amqSourceJcaPooledConnectionFactory(){
        JcaPooledConnectionFactory jca = new JcaPooledConnectionFactory();
        jca.setName("activemq.source");
        jca.setMaxConnections(1);
        jca.setConnectionFactory(amqSourceConnectionFactory());
        jca.setTransactionManager(TransactionManager.transactionManager());
        return jca;
    }

    @Bean
    ActiveMQResourceManager amqSourceResourceManager() {
        ActiveMQResourceManager activeMQResourceManager = new ActiveMQResourceManager();
        activeMQResourceManager.setTransactionManager(TransactionManager.transactionManager());
        activeMQResourceManager.setConnectionFactory(amqSourceJcaPooledConnectionFactory());
        activeMQResourceManager.setResourceName("activemq.source");
        activeMQResourceManager.recoverResource();
        return activeMQResourceManager;
    }

//    <bean id="rm-source-amq" class="org.apache.activemq.pool.ActiveMQResourceManager" init-method="recoverResource">
//    <property name="transactionManager" ref="recoverableTxManager" />
//    <!-- CF must be of type ActiveMQConnectionFactory, otherwise no recovery will occur -->
//    <property name="connectionFactory" ref="AmqXaCF" />
//    <property name="userName" value="${username}" />
//    <property name="password" value="${password}" />
//    <!-- name needs to match name property set on JcaPooledConnectionFactory above -->
//    <property name="resourceName" value="activemq.source" />
//    </bean>





    @Bean
    ActiveMQConfiguration amqSourceConfiguration(){
        ActiveMQConfiguration jcSource = new ActiveMQConfiguration();
        jcSource.setConnectionFactory(amqSourceJcaPooledConnectionFactory());
//        We set local transactions to false, because the JtaTransactionManager
//        will take care of enrolling the XA JMS Connection when needed.
        jcSource.setTransacted(false);
        jcSource.setTransactionManager(platformTransactionManager);
        jcSource.setMaxConcurrentConsumers(1);
        jcSource.setCacheLevelName("CACHE_NONE");
        return jcSource;
    }

    @Bean
    ActiveMQComponent amqSource(){
        return new ActiveMQComponent(amqSourceConfiguration());
    }


    // TARGET CONFIG

    @Bean
    ActiveMQXAConnectionFactory targetCF(){
        ActiveMQXAConnectionFactory targetCF = new ActiveMQXAConnectionFactory();
        targetCF.setBrokerURL(targetBrokerUrl);
        targetCF.setUserName(targetBrokerUsername);
        targetCF.setPassword(targetBrokerPassword);
        return targetCF;
    }

    @Bean
    JcaPooledConnectionFactory amqTargetJcaPooledConnectionFactory(){
        JcaPooledConnectionFactory jca = new JcaPooledConnectionFactory();
        jca.setName("activemq.target");
        jca.setMaxConnections(1);
        jca.setConnectionFactory(targetCF());
        jca.setTransactionManager(TransactionManager.transactionManager());
        return jca;
    }


    @Bean
    ActiveMQConfiguration amqTargetConfiguration(){
        ActiveMQConfiguration ac = new ActiveMQConfiguration();
        ac.setConnectionFactory(amqTargetJcaPooledConnectionFactory());
//        We set local transactions to false, because the JtaTransactionManager
//        will take care of enrolling the XA JMS Connection when needed.
        ac.setTransacted(false);
        ac.setTransactionManager(platformTransactionManager);
        ac.setMaxConcurrentConsumers(1);
        ac.setCacheLevelName("CACHE_NONE");
        return ac;
    }

    @Bean
    ActiveMQResourceManager amqTargetResourceManager(){
        ActiveMQResourceManager amrm = new ActiveMQResourceManager();
        amrm.setTransactionManager(TransactionManager.transactionManager());
        amrm.setConnectionFactory(amqTargetJcaPooledConnectionFactory());
        amrm.setUserName(targetBrokerUsername);
        amrm.setPassword(targetBrokerPassword);
        amrm.setResourceName("activemq.target");
        amrm.recoverResource();
        return amrm;
    }

    @Bean
    ActiveMQComponent amqTarget(){
        return new ActiveMQComponent(amqTargetConfiguration());
    }
}
