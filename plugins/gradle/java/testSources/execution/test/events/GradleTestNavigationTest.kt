// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestNavigationTest : GradleTestNavigationTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7 <=> 7.0")
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 5 OLD`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_PARAMETRISED_JUNIT5_TEST)

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
  fun `test display name and navigation with Java and Junit 5`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_PARAMETRISED_JUNIT5_TEST)

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
    testJunit4Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT4_TEST)
      writeText("src/test/java/org/example/ParametrizedTestCase.java", JAVA_PARAMETRIZED_JUNIT4_TEST)

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
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Test NG`(gradleVersion: GradleVersion) {
    testTestNGProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_TESTNG_TEST)
      writeText("src/test/java/org/example/ParametrizedTestCase.java", JAVA_PARAMETRIZED_TESTNG_TEST)

      executeTasks(":test")

      assertTestTreeView {
        assertNode("Gradle suite") {
          assertNode("Gradle test") {
            assertNode("ParametrizedTestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertNode("parametrized_test[0](1, first)")
              assertNode("parametrized_test[1](2, second)")
              assertNode("parametrized_test[2](3, third)")
            }
            assertNode("TestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertNode("successful_test")
              assertNode("test")
            }
          }
        }
      }
      assertSMTestProxyTree {
        assertNode("Gradle suite") {
          assertNode("Gradle test") {
            assertNode("ParametrizedTestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertValueIfPresent {
                Assertions.assertEquals("ParametrizedTestCase", value.psiClass.name)
              }
              assertNode("parametrized_test[0](1, first)") {
                Assertions.assertEquals("parametrized_test", value.psiMethod.name)
                Assertions.assertEquals("ParametrizedTestCase", value.psiMethod.psiClass.name)
              }
              assertNode("parametrized_test[1](2, second)") {
                Assertions.assertEquals("parametrized_test", value.psiMethod.name)
                Assertions.assertEquals("ParametrizedTestCase", value.psiMethod.psiClass.name)
              }
              assertNode("parametrized_test[2](3, third)") {
                Assertions.assertEquals("parametrized_test", value.psiMethod.name)
                Assertions.assertEquals("ParametrizedTestCase", value.psiMethod.psiClass.name)
              }
            }
            assertNode("TestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertValueIfPresent {
                Assertions.assertEquals("TestCase", value.psiClass.name)
              }
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
    }
  }

  @ParameterizedTest
  @TargetVersions("5.6+")
  @AllGradleVersionsSource
  fun `test display name and navigation with Groovy and Spock`(gradleVersion: GradleVersion) {
    testSpockProject(gradleVersion) {
      writeText("src/test/groovy/org/example/SpockTestCase.groovy", GROOVY_CLASS_WITH_SPOCK_TESTS)

      executeTasks(":test")

      assertTestTreeView {
        assertNode("SpockTestCase") {
          assertNode("failure test")
          assertNode("length of #name is #length") {
            assertNode("length of Spock is 5")
          }
          assertNode("success test")
        }
      }
      assertSMTestProxyTree {
        assertNode("SpockTestCase") {
          Assertions.assertEquals("SpockTestCase", value.psiClass.name)
          assertNode("failure test") {
            Assertions.assertEquals("failure test", value.psiMethod.name)
            Assertions.assertEquals("SpockTestCase", value.psiMethod.psiClass.name)
          }
          assertNode("length of #name is #length") {
            Assertions.assertEquals("length of #name is #length", value.psiMethod.name)
            Assertions.assertEquals("SpockTestCase", value.psiMethod.psiClass.name)
            assertNode("length of Spock is 5") {
              Assertions.assertEquals("length of #name is #length", value.psiMethod.name)
              Assertions.assertEquals("SpockTestCase", value.psiMethod.psiClass.name)
            }
          }
          assertNode("success test") {
            Assertions.assertEquals("success test", value.psiMethod.name)
            Assertions.assertEquals("SpockTestCase", value.psiMethod.psiClass.name)
          }
        }
      }
    }
  }

  companion object {

    private val JAVA_PARAMETRISED_JUNIT5_TEST = """
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

    private val JAVA_JUNIT4_TEST = """
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

    private val JAVA_PARAMETRIZED_JUNIT4_TEST = """
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

    private val JAVA_TESTNG_TEST = """
      |package org.example;
      |
      |import org.testng.annotations.Ignore;
      |import org.testng.annotations.Test;
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

    private val JAVA_PARAMETRIZED_TESTNG_TEST = """
      |package org.example;
      |
      |import org.testng.annotations.DataProvider;
      |import org.testng.annotations.Test;
      |
      |public class ParametrizedTestCase {
      |
      |    @Test(dataProvider = "data")
      |    public void parametrized_test(int value, String name) {}
      |
      |    @DataProvider(name = "data")
      |    public static Object[][] data() {
      |        return new Object[][]{
      |                {1, "first"},
      |                {2, "second"},
      |                {3, "third"}
      |        };
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
      |  def "success test"() {
      |    expect:
      |    true
      |  }
      |
      |  def "failure test"() {
      |    expect:
      |    false
      |  }
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