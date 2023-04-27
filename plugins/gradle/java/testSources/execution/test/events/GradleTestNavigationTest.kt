// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestNavigationTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7 <=> 7.0")
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 5 OLD`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_PARAMETRISED_JUNIT5_TEST)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertPsiLocation("TestCase")
          assertNode("test") {
            assertPsiLocation("TestCase", "test")
          }
          assertNode("successful test") {
            assertPsiLocation("TestCase", "successful_test")
          }
          assertNode("pretty test") {
            assertPsiLocation("TestCase", "ugly_test")
          }
          assertNode("parametrized_test [1] 1, first") {
            assertPsiLocation("TestCase", "parametrized_test")
          }
          assertNode("parametrized_test [2] 2, second") {
            assertPsiLocation("TestCase", "parametrized_test")
          }
          assertNode("ugly_parametrized_test [1] 3, third") {
            assertPsiLocation("TestCase", "ugly_parametrized_test")
          }
          assertNode("ugly_parametrized_test [2] 4, fourth") {
            assertPsiLocation("TestCase", "ugly_parametrized_test")
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
          assertPsiLocation("TestCase")
          assertNode("test") {
            assertPsiLocation("TestCase", "test")
          }
          assertNode("successful test") {
            assertPsiLocation("TestCase", "successful_test")
          }
          assertNode("pretty test") {
            assertPsiLocation("TestCase", "ugly_test")
          }
          assertNode("parametrized test") {
            if (isTestLauncherSupported()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              assertPsiLocation("TestCase", "parametrized_test")
            }
            assertNode("[1] 1, first") {
              assertPsiLocation("TestCase", "parametrized_test")
            }
            assertNode("[2] 2, second") {
              assertPsiLocation("TestCase", "parametrized_test")
            }
          }
          assertNode("pretty parametrized test") {
            if (isTestLauncherSupported()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              assertPsiLocation("TestCase", "ugly_parametrized_test")
            }
            assertNode("[1] 3, third") {
              assertPsiLocation("TestCase", "ugly_parametrized_test")
            }
            assertNode("[2] 4, fourth") {
              assertPsiLocation("TestCase", "ugly_parametrized_test")
            }
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
        assertNode("TestCase") {
          assertPsiLocation("TestCase")
          assertNode("test") {
            assertPsiLocation("TestCase", "test")
          }
          assertNode("successful_test") {
            assertPsiLocation("TestCase", "successful_test")
          }
        }
        assertNode("ParametrizedTestCase") {
          assertPsiLocation("ParametrizedTestCase")
          assertNode("parametrized_test[0]") {
            assertPsiLocation("ParametrizedTestCase", "parametrized_test")
          }
          assertNode("parametrized_test[1]") {
            assertPsiLocation("ParametrizedTestCase", "parametrized_test")
          }
          assertNode("parametrized_test[2]") {
            assertPsiLocation("ParametrizedTestCase", "parametrized_test")
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
            assertNode("TestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertPsiLocation("TestCase")
              assertNode("test") {
                assertPsiLocation("TestCase", "test")
              }
              assertNode("successful_test") {
                assertPsiLocation("TestCase", "successful_test")
              }
            }
            assertNode("ParametrizedTestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertPsiLocation("ParametrizedTestCase")
              assertNode("parametrized_test[0](1, first)") {
                assertPsiLocation("ParametrizedTestCase", "parametrized_test")
              }
              assertNode("parametrized_test[1](2, second)") {
                assertPsiLocation("ParametrizedTestCase", "parametrized_test")
              }
              assertNode("parametrized_test[2](3, third)") {
                assertPsiLocation("ParametrizedTestCase", "parametrized_test")
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
          assertPsiLocation("SpockTestCase")
          assertNode("success test") {
            assertPsiLocation("SpockTestCase", "success test")
          }
          assertNode("failure test") {
            assertPsiLocation("SpockTestCase", "failure test")
          }
          assertNode("length of #name is #length") {
            assertPsiLocation("SpockTestCase", "length of #name is #length")
            assertNode("length of Spock is 5") {
              assertPsiLocation("SpockTestCase", "length of #name is #length")
            }
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