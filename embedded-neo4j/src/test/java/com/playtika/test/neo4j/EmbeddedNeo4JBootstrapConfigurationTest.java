/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Playtika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.playtika.test.neo4j;

import static com.playtika.test.neo4j.Neo4jProperties.BEAN_NAME_EMBEDDED_NEO4J;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import com.playtika.test.common.operations.NetworkTestOperations;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.types.Node;
import org.neo4j.ogm.session.SessionFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest(
        classes = EmbeddedNeo4JBootstrapConfigurationTest.TestConfiguration.class,
        properties = "embedded.neo4j.install.enabled=true"
)
@ActiveProfiles("enabled")
public class EmbeddedNeo4JBootstrapConfigurationTest {

    @Autowired
    Driver driver;

    @Autowired
    PersonRepository personRepository;

    @Autowired
    ConfigurableListableBeanFactory beanFactory;

    @Autowired
    NetworkTestOperations neo4jNetworkTestOperations;

    @Configuration
    @EnableAutoConfiguration
    @EnableNeo4jRepositories
    static class TestConfiguration {
    }

    @Autowired
    ConfigurableEnvironment environment;


    @Test
    void springDataNeo4jShouldWork() {
        long friendId;

        try (Session session = driver.session()) {
            Record record = session.run("CREATE (n:Person{name:'Freddie'}),"
                    + " (n)-[:TEAMMATE{since: 1995}]->(:Person{name:'Frank'})"
                    + "RETURN n").single();

            Node friendNode = record.get("n").asNode();
            friendId = friendNode.id();
        }

        Person person = personRepository.findById(friendId).get();

        Set<TeamMateRelationship> loadedRelationship = person.getTeammates();
        assertThat(loadedRelationship).allSatisfy(relationship -> {
            assertThat(relationship.getSince()).isEqualTo(1995);
            assertThat(relationship.getTeamMate().getName()).isEqualTo("Frank");
        });
    }

    @Test
    public void shouldEmulateLatency() throws Exception {
        neo4jNetworkTestOperations.withNetworkLatency(ofMillis(1000),
                () -> assertThat(durationOf(() -> personRepository.findByName("any")))
                        .isGreaterThan(1000L)
        );

        assertThat(durationOf(() -> personRepository.findByName("any")))
                .isLessThan(100L);
    }

    @Test
    public void shouldSetupDependsOnForAllClients() throws Exception {
        String[] beanNamesForType = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Driver.class);
        assertThat(beanNamesForType)
                .as("neo4jDriver should be present")
                .hasSize(1)
                .contains("neo4jDriver");
        asList(beanNamesForType).forEach(this::hasDependsOn);
    }

    private void hasDependsOn(String beanName) {
        assertThat(beanFactory.getBeanDefinition(beanName).getDependsOn())
                .isNotNull()
                .isNotEmpty()
                .contains(BEAN_NAME_EMBEDDED_NEO4J);
    }

    private static long durationOf(Callable<?> op) throws Exception {
        long start = System.currentTimeMillis();
        op.call();
        return System.currentTimeMillis() - start;
    }

    @Test
    public void propertiesAreAvailable() {
        assertThat(environment.getProperty("embedded.neo4j.httpsPort")).isNotEmpty();
        assertThat(environment.getProperty("embedded.neo4j.boltPort")).isNotEmpty();
        assertThat(environment.getProperty("embedded.neo4j.host")).isNotEmpty();
        assertThat(environment.getProperty("embedded.neo4j.user")).isNotEmpty();
        assertThat(environment.getProperty("embedded.neo4j.password")).isNotEmpty();
    }
}
