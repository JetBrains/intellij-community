// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.gradle.testFramework.GradleTestExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.junit.jupiter.params.ParameterizedTest

class GradleTestNavigationTest : GradleTestExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 5 OLD`(gradleVersion: GradleVersion) {
    assumeThatGradleIsOlderThan(gradleVersion, "7.0")
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT5_TEST)
      writeText("src/test/java/org/example/DisplayNameTestCase.java", JAVA_DISPLAY_NAME_JUNIT5_TEST)

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("TestCase") {
          assertPsiLocation("TestCase")
          assertNode("test") {
            assertPsiLocation("TestCase", "test")
          }
          assertNode("successful_test") {
            assertPsiLocation("TestCase", "successful_test")
          }
          assertNode("parametrized_test [1] 1, first") {
            assertPsiLocation("TestCase", "parametrized_test", "[1]")
          }
          assertNode("parametrized_test [2] 2, second") {
            assertPsiLocation("TestCase", "parametrized_test", "[2]")
          }
          assertNode("dynamic_test dynamic first") {
            assertPsiLocation("TestCase", "dynamic_test", "[1]")
          }
          assertNode("dynamic_test dynamic second") {
            assertPsiLocation("TestCase", "dynamic_test", "[2]")
          }
        }
        assertNode("DisplayNameTestCase") {
          assertPsiLocation("DisplayNameTestCase")
          assertNode("test") {
            assertPsiLocation("DisplayNameTestCase", "test")
          }
          assertNode("successful test") {
            assertPsiLocation("DisplayNameTestCase", "successful_test")
          }
          assertNode("pretty test") {
            assertPsiLocation("DisplayNameTestCase", "ugly_test")
          }
          assertNode("parametrized_test [1] 1, first") {
            assertPsiLocation("DisplayNameTestCase", "parametrized_test", "[1]")
          }
          assertNode("parametrized_test [2] 2, second") {
            assertPsiLocation("DisplayNameTestCase", "parametrized_test", "[2]")
          }
          assertNode("ugly_parametrized_test [1] 3, third") {
            assertPsiLocation("DisplayNameTestCase", "ugly_parametrized_test", "[1]")
          }
          assertNode("ugly_parametrized_test [2] 4, fourth") {
            assertPsiLocation("DisplayNameTestCase", "ugly_parametrized_test", "[2]")
          }
          assertNode("dynamic_test dynamic first") {
            assertPsiLocation("DisplayNameTestCase", "dynamic_test", "[1]")
          }
          assertNode("dynamic_test dynamic second") {
            assertPsiLocation("DisplayNameTestCase", "dynamic_test", "[2]")
          }
          assertNode("ugly_dynamic_test dynamic first") {
            assertPsiLocation("DisplayNameTestCase", "ugly_dynamic_test", "[1]")
          }
          assertNode("ugly_dynamic_test dynamic second") {
            assertPsiLocation("DisplayNameTestCase", "ugly_dynamic_test", "[2]")
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test display name generated and navigation with Java and Junit 5`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/DisplayNameGeneratedTestCase.java", JAVA_DISPLAY_NAME_GENERATOR_JUNIT5_TEST)

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("DisplayNameGeneratedTestCase[Gradle '8.1']") {
          assertPsiLocation("DisplayNameGeneratedTestCase")
          assertNode("test") {
            assertPsiLocation("DisplayNameGeneratedTestCase", "test")
          }
          //assertNode("InnerTest") {
          //  assertPsiLocation("InnerTest")
          //  assertNode("test") {
          //    assertPsiLocation("InnerTest", "test")
          //  }
          //}
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test display name and navigation with Java and Junit 5`(gradleVersion: GradleVersion) {
    assumeThatGradleIsAtLeast(gradleVersion, "7.0")
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT5_TEST)
      writeText("src/test/java/org/example/DisplayNameTestCase.java", JAVA_DISPLAY_NAME_JUNIT5_TEST)

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("TestCase") {
          assertPsiLocation("TestCase")
          assertNode("test") {
            assertPsiLocation("TestCase", "test")
          }
          assertNode("successful_test") {
            assertPsiLocation("TestCase", "successful_test")
          }
          assertNode("parametrized_test(int, String)") {
            assertPsiLocation("TestCase", "parametrized_test")
            assertNode("[1] 1, first") {
              assertPsiLocation("TestCase", "parametrized_test", "[1]")
            }
            assertNode("[2] 2, second") {
              assertPsiLocation("TestCase", "parametrized_test", "[2]")
            }
          }
          assertNode("dynamic_test") {
            assertPsiLocation("TestCase", "dynamic_test")
            assertNode("dynamic first") {
              assertPsiLocation("TestCase", "dynamic_test", "[1]")
            }
            assertNode("dynamic second") {
              assertPsiLocation("TestCase", "dynamic_test", "[2]")
            }
          }
        }
        assertNode("DisplayNameTestCase") {
          assertPsiLocation("DisplayNameTestCase")
          assertNode("test") {
            assertPsiLocation("DisplayNameTestCase", "test")
          }
          assertNode("successful test") {
            assertPsiLocation("DisplayNameTestCase", "successful_test")
          }
          assertNode("pretty test") {
            assertPsiLocation("DisplayNameTestCase", "ugly_test")
          }
          assertNode("parametrized test (int, String)") {
            if (isBuiltInTestEventsUsed()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              assertPsiLocation("DisplayNameTestCase", "parametrized_test")
            }
            assertNode("[1] 1, first") {
              assertPsiLocation("DisplayNameTestCase", "parametrized_test", "[1]")
            }
            assertNode("[2] 2, second") {
              assertPsiLocation("DisplayNameTestCase", "parametrized_test", "[2]")
            }
          }
          assertNode("pretty parametrized test") {
            if (isBuiltInTestEventsUsed()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              assertPsiLocation("DisplayNameTestCase", "ugly_parametrized_test")
            }
            assertNode("[1] 3, third") {
              assertPsiLocation("DisplayNameTestCase", "ugly_parametrized_test", "[1]")
            }
            assertNode("[2] 4, fourth") {
              assertPsiLocation("DisplayNameTestCase", "ugly_parametrized_test", "[2]")
            }
          }
          assertNode("dynamic test") {
            if (isBuiltInTestEventsUsed()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              assertPsiLocation("DisplayNameTestCase", "dynamic_test")
            }
            assertNode("dynamic first") {
              assertPsiLocation("DisplayNameTestCase", "dynamic_test", "[1]")
            }
            assertNode("dynamic second") {
              assertPsiLocation("DisplayNameTestCase", "dynamic_test", "[2]")
            }
          }
          assertNode("pretty dynamic test") {
            if (isBuiltInTestEventsUsed()) {
              // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
              assertPsiLocation("DisplayNameTestCase", "ugly_dynamic_test")
            }
            assertNode("dynamic first") {
              assertPsiLocation("DisplayNameTestCase", "ugly_dynamic_test", "[1]")
            }
            assertNode("dynamic second") {
              assertPsiLocation("DisplayNameTestCase", "ugly_dynamic_test", "[2]")
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

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
            assertPsiLocation("ParametrizedTestCase", "parametrized_test", "[0]")
          }
          assertNode("parametrized_test[1]") {
            assertPsiLocation("ParametrizedTestCase", "parametrized_test", "[1]")
          }
          assertNode("parametrized_test[2]") {
            assertPsiLocation("ParametrizedTestCase", "parametrized_test", "[2]")
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

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
                assertPsiLocation("ParametrizedTestCase", "parametrized_test", "[0]")
              }
              assertNode("parametrized_test[1](2, second)") {
                assertPsiLocation("ParametrizedTestCase", "parametrized_test", "[1]")
              }
              assertNode("parametrized_test[2](3, third)") {
                assertPsiLocation("ParametrizedTestCase", "parametrized_test", "[2]")
              }
            }
          }
        }
      }
    }
  }

  companion object {

    @Language("JAVA")
    private val JAVA_JUNIT5_TEST = """
      |package org.example;
      |
      |import org.junit.jupiter.api.*;
      |import org.junit.jupiter.params.ParameterizedTest;
      |import org.junit.jupiter.params.provider.CsvSource;
      |
      |import java.util.Arrays;
      |import java.util.List;
      |
      |public class TestCase {
      |
      |    @Test
      |    public void test() {}
      |
      |    @Test
      |    public void successful_test() {}
      |
      |    @ParameterizedTest
      |    @CsvSource({"1, 'first'", "2, 'second'"})
      |    public void parametrized_test(int value, String name) {}
      |
      |    @TestFactory
      |    public List<DynamicTest> dynamic_test() {
      |        return Arrays.asList(
      |            DynamicTest.dynamicTest("dynamic first", () -> {}),
      |            DynamicTest.dynamicTest("dynamic second", () -> {})
      |        );
      |    }
      |}
    """.trimMargin()

    @Language("JAVA")
    private val JAVA_DISPLAY_NAME_JUNIT5_TEST = """
      |package org.example;
      |
      |import org.junit.jupiter.api.*;
      |import org.junit.jupiter.params.ParameterizedTest;
      |import org.junit.jupiter.params.provider.CsvSource;
      |
      |import java.util.Arrays;
      |import java.util.List;
      |
      |@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
      |public class DisplayNameTestCase {
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
      |
      |    @TestFactory
      |    public List<DynamicTest> dynamic_test() {
      |        return Arrays.asList(
      |            DynamicTest.dynamicTest("dynamic first", () -> {}),
      |            DynamicTest.dynamicTest("dynamic second", () -> {})
      |        );
      |    }
      |
      |    @TestFactory
      |    @DisplayName("pretty dynamic test")
      |    public List<DynamicTest> ugly_dynamic_test() {
      |        return Arrays.asList(
      |            DynamicTest.dynamicTest("dynamic first", () -> {}),
      |            DynamicTest.dynamicTest("dynamic second", () -> {})
      |        );
      |    }
      |}
    """.trimMargin()

    @Language("JAVA")
    private val JAVA_DISPLAY_NAME_GENERATOR_JUNIT5_TEST = """
      |package org.example;
      |
      |import org.junit.jupiter.api.*;
      |
      |@DisplayNameGeneration(DisplayNameGeneratedTestCase.CustomNameGenerator.class)
      |public class DisplayNameGeneratedTestCase {
      |
      |    @Test
      |    public void test() {}
      |
      |    public static class CustomNameGenerator extends DisplayNameGenerator.Standard {
      |        @Override
      |        public String generateDisplayNameForClass(Class<?> testClass) {
      |            return super.generateDisplayNameForClass(testClass) + "[Gradle '8.1']";
      |        }
      |    }
      //|    
      //|    @Nested
      //|    public class InnerTest {
      //|      @Test
      //|      public void test() {}
      //|    }
      |}
    """.trimMargin()

    @Language("JAVA")
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

    @Language("JAVA")
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

    @Language("JAVA")
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

    @Language("JAVA")
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
  }
}