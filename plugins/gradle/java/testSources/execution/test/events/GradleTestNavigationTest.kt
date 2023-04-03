// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestNavigationTest : GradleTestNavigationTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7 <=> 7.0")
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 5`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_PARAMETRISED_JUNIT_5_TEST)

      executeTasks(":test")

      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("parametrized_test [1] 1, first")
          assertNode("parametrized_test [2] 2, second")
          assertNode("pretty test")
          assertNode("successful test")
          assertNode("test")
          assertNode("ugly_parametrized_test [1] 3, third")
          assertNode("ugly_parametrized_test [2] 4, fourth")
        }
      }
      assertSMTestProxyTree {
        assertNode("TestCase") {
          Assertions.assertEquals("TestCase", value.psiClass.name)
          assertNode("parametrized_test [1] 1, first") {
            Assertions.assertEquals("parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("parametrized_test [2] 2, second") {
            Assertions.assertEquals("parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("pretty test") {
            Assertions.assertEquals("ugly_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("successful test") {
            Assertions.assertEquals("successful_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("test") {
            Assertions.assertEquals("test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("ugly_parametrized_test [1] 3, third") {
            Assertions.assertEquals("ugly_parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("ugly_parametrized_test [2] 4, fourth") {
            Assertions.assertEquals("ugly_parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
        }
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("7.0+")
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 5 and test events`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_PARAMETRISED_JUNIT_5_TEST)

      executeTasks(":test")

      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("parametrized test") {
            assertNode("[1] 1, first")
            assertNode("[2] 2, second")
          }
          assertNode("pretty parametrized test") {
            assertNode("[1] 3, third")
            assertNode("[2] 4, fourth")
          }
          assertNode("pretty test")
          assertNode("successful test")
          assertNode("test")
        }
      }
      assertSMTestProxyTree {
        assertNode("TestCase") {
          Assertions.assertEquals("TestCase", value.psiClass.name)
          assertNode("parametrized test") {
            if (isSupportedTestLauncher()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              Assertions.assertEquals("parametrized_test", value.psiMethod.name)
              Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
            }
            assertNode("[1] 1, first") {
              Assertions.assertEquals("parametrized_test", value.psiMethod.name)
              Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
            }
            assertNode("[2] 2, second") {
              Assertions.assertEquals("parametrized_test", value.psiMethod.name)
              Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
            }
          }
          assertNode("pretty parametrized test") {
            if (isSupportedTestLauncher()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              Assertions.assertEquals("ugly_parametrized_test", value.psiMethod.name)
              Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
            }
            assertNode("[1] 3, third") {
              Assertions.assertEquals("ugly_parametrized_test", value.psiMethod.name)
              Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
            }
            assertNode("[2] 4, fourth") {
              Assertions.assertEquals("ugly_parametrized_test", value.psiMethod.name)
              Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
            }
          }
          assertNode("pretty test") {
            Assertions.assertEquals("ugly_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("successful test") {
            Assertions.assertEquals("successful_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("test") {
            Assertions.assertEquals("test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 4`(gradleVersion: GradleVersion) {
    test(gradleVersion, JAVA_JUNIT4_FIXTURE) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT_4_TEST)
      writeText("src/test/java/org/example/ParametrizedTestCase.java", JAVA_PARAMETRIZED_JUNIT_4_TEST)

      executeTasks(":test")

      assertTestTreeView {
        assertNode("ParametrizedTestCase") {
          assertNode("parametrized_test[0]")
          assertNode("parametrized_test[1]")
          assertNode("parametrized_test[2]")
        }
        assertNode("TestCase") {
          assertNode("successful_test")
          assertNode("test")
        }
      }
      assertSMTestProxyTree {
        assertNode("ParametrizedTestCase") {
          Assertions.assertEquals("ParametrizedTestCase", value.psiClass.name)
          assertNode("parametrized_test[0]") {
            Assertions.assertEquals("parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("ParametrizedTestCase", value.psiMethod.psiClass.name)
          }
          assertNode("parametrized_test[1]") {
            Assertions.assertEquals("parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("ParametrizedTestCase", value.psiMethod.psiClass.name)
          }
          assertNode("parametrized_test[2]") {
            Assertions.assertEquals("parametrized_test", value.psiMethod.name)
            Assertions.assertEquals("ParametrizedTestCase", value.psiMethod.psiClass.name)
          }
        }
        assertNode("TestCase") {
          Assertions.assertEquals("TestCase", value.psiClass.name)
          assertNode("successful_test") {
            Assertions.assertEquals("successful_test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
          assertNode("test") {
            Assertions.assertEquals("test", value.psiMethod.name)
            Assertions.assertEquals("TestCase", value.psiMethod.psiClass.name)
          }
        }
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("5.6+")
  @AllGradleVersionsSource
  fun `test display name and navigation with Groovy and Spock`(gradleVersion: GradleVersion) {
    test(gradleVersion, GROOVY_SPOCK_FIXTURE) {
      writeText("src/test/groovy/org/example/SpockTestCase.groovy", GROOVY_CLASS_WITH_SPOCK_TESTS)

      executeTasks(":test")

      assertTestTreeView {
        assertNode("SpockTestCase") {
          assertNode("length of #name is #length") {
            assertNode("length of Spock is 5")
          }
        }
      }
      assertSMTestProxyTree {
        assertNode("SpockTestCase") {
          Assertions.assertEquals("SpockTestCase", value.psiClass.name)
          assertNode("length of #name is #length") {
            Assertions.assertEquals("length of #name is #length", value.psiMethod.name)
            Assertions.assertEquals("SpockTestCase", value.psiMethod.psiClass.name)
            assertNode("length of Spock is 5") {
              Assertions.assertEquals("length of #name is #length", value.psiMethod.name)
              Assertions.assertEquals("SpockTestCase", value.psiMethod.psiClass.name)
            }
          }
        }
      }
    }
  }

  companion object {

    private val JAVA_JUNIT4_FIXTURE = GradleTestFixtureBuilder.create("GradleTestNavigationTest-java-junit4") { gradleVersion ->
      withSettingsFile {
        setProjectName("GradleTestNavigationTest-java-junit4")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit4()
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private val GROOVY_SPOCK_FIXTURE = GradleTestFixtureBuilder.create("GradleTestNavigationTest-groovy-spock") { gradleVersion ->
      withSettingsFile {
        setProjectName("GradleTestNavigationTest-groovy-spock")
      }
      withBuildFile(gradleVersion) {
        withGroovyPlugin("3.0.0")
        addTestImplementationDependency(call("platform", "org.spockframework:spock-bom:2.1-groovy-3.0"))
        addTestImplementationDependency("org.spockframework:spock-core:2.1-groovy-3.0")
        withJUnit()
      }
      withDirectory("src/main/groovy")
      withDirectory("src/test/groovy")
    }

    private val JAVA_PARAMETRISED_JUNIT_5_TEST = """
      |package org.example;
      |
      |import org.junit.jupiter.api.*;
      |import org.junit.jupiter.params.ParameterizedTest;
      |import org.junit.jupiter.params.provider.CsvSource;
      |
      |@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
      |public class TestCase {
      |
      |    @Test
      |    public void test() {}
      |
      |    @Test
      |    public void successful_test() {}
      |
      |    @Test
      |    @DisplayName("pretty test")
      |    public void ugly_test() {}
      |
      |    @ParameterizedTest
      |    @CsvSource({"1, 'first'", "2, 'second'"})
      |    public void parametrized_test(int value, String name) {}
      |
      |    @ParameterizedTest
      |    @DisplayName("pretty parametrized test")
      |    @CsvSource({"3, 'third'", "4, 'fourth'"})
      |    public void ugly_parametrized_test(int value, String name) {}
      |}
    """.trimMargin()

    private val JAVA_JUNIT_4_TEST = """
      |package org.example;
      |
      |import org.junit.Test;
      |
      |public class TestCase {
      |
      |    @Test
      |    public void test() {}
      |
      |    @Test
      |    public void successful_test() {}
      |}
    """.trimMargin()

    private val JAVA_PARAMETRIZED_JUNIT_4_TEST = """
      |package org.example;
      |
      |import org.junit.Test;
      |import org.junit.runner.RunWith;
      |import org.junit.runners.Parameterized;
      |
      |import java.util.Arrays;
      |import java.util.Collection;
      |
      |@RunWith(Parameterized.class)
      |public class ParametrizedTestCase {
      |
      |    private final int value;
      |    private final String name;
      |
      |    public ParametrizedTestCase(int value, String name) {
      |        this.value = value;
      |        this.name = name;
      |    }
      |
      |    @Test
      |    public void parametrized_test() {}
      |
      |    @Parameterized.Parameters
      |    public static Collection<Object[]> data() {
      |        return Arrays.asList(new Object[][]{
      |                {1, "first"},
      |                {2, "second"},
      |                {3, "third"}
      |        });
      |    }
      |}
    """.trimMargin()

    private val GROOVY_CLASS_WITH_SPOCK_TESTS = """
      |package org.example
      |
      |import spock.lang.Specification
      |
      |class SpockTestCase extends Specification {
      |
      |  def "length of #name is #length"() {
      |    expect:
      |    name.size() != length
      |
      |    where:
      |    name     | length
      |    "Spock"  | 5
      |  }
      |}
    """.trimMargin()
  }
}