// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.openapi.application.PathManager
import com.intellij.rt.execution.junit.FileComparisonFailure
import junit.framework.ComparisonFailure
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestAssertionTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7+")
  @AllGradleVersionsSource
  fun `test assertion result of Junit 5`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT5_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test assert equals for ints") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :5
              |Actual   :2
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message ==> expected: <5> but was: <2>
            """.trimMargin())
          }
          assertNode("test assert equals for texts") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message ==> expected: <expected text> but was: <actual text>
            """.trimMargin())
          }
          assertNode("test assert equals for objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message ==> expected: <expected text> but was: <actual text>
            """.trimMargin())
          }
          assertNode("test assert equals for same objects") {
            // Test output has object hashes, it is hard to predict and assert
            assertTestConsoleContains("""
              |<Click to see difference>
            """.trimMargin())
          }
          assertNode("test assert same for objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message ==> expected: <expected text> but was: <actual text>
            """.trimMargin())
          }
          assertNode("test assert same for same objects") {
            // Test output has object hashes, it is hard to predict and assert
            assertTestConsoleContains("""
              |<Click to see difference>
            """.trimMargin())
          }
          assertNode("test multiple assert equals for texts") {
            if (isTestLauncherSupported()) {
              assertTestConsoleContains("""
                |
                |assertion message 1
                |Expected :expected text 1
                |Actual   :actual text 1
                |<Click to see difference>
                |
                |org.opentest4j.AssertionFailedError: assertion message 1 ==> expected: <expected text 1> but was: <actual text 1>
              """.trimMargin())
              assertTestConsoleContains("""
                |
                |assertion message 2
                |Expected :expected text 2
                |Actual   :actual text 2
                |<Click to see difference>
                |
                |org.opentest4j.AssertionFailedError: assertion message 2 ==> expected: <expected text 2> but was: <actual text 2>
              """.trimMargin())
              assertTestConsoleContains("""
                |
                |assertion message 3
                |Expected :expected text 3
                |Actual   :actual text 3
                |<Click to see difference>
                |
                |org.opentest4j.AssertionFailedError: assertion message 3 ==> expected: <expected text 3> but was: <actual text 3>
              """.trimMargin())
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("7.6+")
  @AllGradleVersionsSource
  fun `test assertion result of raw Junit 5`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_RAW_JUNIT5_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test assert equals for ints") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :5
              |Actual   :2
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message
            """.trimMargin())
          }
          assertNode("test assert equals for texts") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message
            """.trimMargin())
          }
          assertNode("test assert equals for objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message
            """.trimMargin())
          }
          assertNode("test assert equals for same objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :string
              |Actual   :string
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: assertion message
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("4.7+")
  @AllGradleVersionsSource
  fun `test assertion result of AssertJ`(gradleVersion: GradleVersion) {
    testJunit5AssertJProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_ASSERTJ_JUNIT5_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test assert equals for ints") {
            assertTestConsoleContains("""
              |
              |Expected :5
              |Actual   :2
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: 
              |expected: 5
              | but was: 2
            """.trimMargin())
          }
          assertNode("test assert equals for texts") {
            assertTestConsoleContains("""
              |
              |Expected :"expected text"
              |Actual   :"actual text"
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: 
              |expected: "expected text"
              | but was: "actual text"
            """.trimMargin())
          }
          assertNode("test assert equals for objects") {
            assertTestConsoleContains("""
              |
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: 
              |expected: expected text
              | but was: actual text
            """.trimMargin())
          }
          assertNode("test assert equals for same objects") {
            // Test output has object hashes, it is hard to predict and assert
            assertTestConsoleContains("""
              |<Click to see difference>
            """.trimMargin())
          }
          assertNode("test assert same for objects") {
            assertTestConsoleContains("""
              |
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |java.lang.AssertionError: 
              |Expecting actual:
              |  actual text
              |and:
              |  expected text
            """.trimMargin())
          }
          assertNode("test assert same for same objects") {
            assertTestConsoleContains("""
              |
              |Expected :string
              |Actual   :string
              |<Click to see difference>
              |
              |java.lang.AssertionError: 
              |Expecting actual:
              |  string
              |and:
              |  string
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Junit 4`(gradleVersion: GradleVersion) {
    testJunit4Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT4_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test_assert_equals_for_ints") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :5
              |Actual   :2
              |<Click to see difference>
              |
              |java.lang.AssertionError: assertion message expected:<5> but was:<2>
            """.trimMargin())
          }
          assertNode("test_assert_equals_for_texts") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |org.junit.ComparisonFailure: assertion message expected:<[expected] text> but was:<[actual] text>
            """.trimMargin())
          }
          assertNode("test_assert_equals_for_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |java.lang.AssertionError: assertion message expected:<expected text> but was:<actual text>
            """.trimMargin())
          }
          assertNode("test_assert_equals_for_same_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :org.example.TestCase${'$'}3<string>
              |Actual   :org.example.TestCase${'$'}4<string>
              |<Click to see difference>
              |
              |java.lang.AssertionError: assertion message expected: org.example.TestCase${'$'}3<string> but was: org.example.TestCase${'$'}4<string>
            """.trimMargin())
          }
          assertNode("test_assert_same_for_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |java.lang.AssertionError: assertion message expected same:<expected text> was not:<actual text>
            """.trimMargin())
          }
          assertNode("test_assert_same_for_same_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :string
              |Actual   :string
              |<Click to see difference>
              |
              |java.lang.AssertionError: assertion message expected same:<string> was not:<string>
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of deprecated Junit 4 (Junit 3)`(gradleVersion: GradleVersion) {
    testJunit4Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_DEPRECATED_JUNIT4_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test_assert_equals_for_ints") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :5
              |Actual   :2
              |<Click to see difference>
              |
              |junit.framework.AssertionFailedError: assertion message expected:<5> but was:<2>
            """.trimMargin())
          }
          assertNode("test_assert_equals_for_texts") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |junit.framework.ComparisonFailure: assertion message expected:<[expected] text> but was:<[actual] text>
            """.trimMargin())
          }
          assertNode("test_assert_equals_for_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |junit.framework.AssertionFailedError: assertion message expected:<expected text> but was:<actual text>
            """.trimMargin())
          }
          assertNode("test_assert_equals_for_same_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :string
              |Actual   :string
              |<Click to see difference>
              |
              |junit.framework.AssertionFailedError: assertion message expected:<string> but was:<string>
            """.trimMargin())
          }
          assertNode("test_assert_same_for_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :expected text
              |Actual   :actual text
              |<Click to see difference>
              |
              |junit.framework.AssertionFailedError: assertion message expected same:<expected text> was not:<actual text>
            """.trimMargin())
          }
          assertNode("test_assert_same_for_same_objects") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :string
              |Actual   :string
              |<Click to see difference>
              |
              |junit.framework.AssertionFailedError: assertion message expected same:<string> was not:<string>
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Test NG`(gradleVersion: GradleVersion) {
    testTestNGProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_TESTNG_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("Gradle suite") {
          assertNode("Gradle test") {
            assertNode("TestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertNode("test_assert_equals_for_ints") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :5
                  |Actual   :2
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected [5] but found [2]
                """.trimMargin())
              }
              assertNode("test_assert_equals_for_texts") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :expected text
                  |Actual   :actual text
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected [expected text] but found [actual text]
                """.trimMargin())
              }
              assertNode("test_assert_equals_for_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :expected text
                  |Actual   :actual text
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected [expected text] but found [actual text]
                """.trimMargin())
              }
              assertNode("test_assert_equals_for_same_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :string
                  |Actual   :string
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected [string] but found [string]
                """.trimMargin())
              }
              assertNode("test_assert_same_for_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :expected text
                  |Actual   :actual text
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected [expected text] but found [actual text]
                """.trimMargin())
              }
              assertNode("test_assert_same_for_same_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :string
                  |Actual   :string
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected [string] but found [string]
                """.trimMargin())
              }
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Test NG (Junit)`(gradleVersion: GradleVersion) {
    testTestNGProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", JAVA_JUNIT_TESTNG_TESTS)

      executeTasks(":test")
      assertTestTreeView {
        assertNode("Gradle suite") {
          assertNode("Gradle test") {
            assertNode("TestCase", flattenIf = isGradleOlderThan("5.0")) {
              assertNode("test_assert_equals_for_ints") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :5
                  |Actual   :2
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected:<5> but was:<2>
                """.trimMargin())
              }
              assertNode("test_assert_equals_for_texts") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :expected text
                  |Actual   :actual text
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected:<expected text> but was:<actual text>
                """.trimMargin())
              }
              assertNode("test_assert_equals_for_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :expected text
                  |Actual   :actual text
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected:<expected text> but was:<actual text>
                """.trimMargin())
              }
              assertNode("test_assert_equals_for_same_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :string
                  |Actual   :string
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected:<string> but was:<string>
                """.trimMargin())
              }
              assertNode("test_assert_same_for_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :expected text
                  |Actual   :actual text
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected same:<expected text> was not:<actual text>
                """.trimMargin())
              }
              assertNode("test_assert_same_for_same_objects") {
                assertTestConsoleContains("""
                  |
                  |assertion message
                  |Expected :string
                  |Actual   :string
                  |<Click to see difference>
                  |
                  |java.lang.AssertionError: assertion message expected same:<string> was not:<string>
                """.trimMargin())
              }
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test intellij file comparison test`(gradleVersion: GradleVersion) {
    val fixture = GradleTestFixtureBuilder.create("GradleTestAssertionTest-file-comparison") {
      withSettingsFile {
        setProjectName("GradleTestAssertionTest-file-comparison")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit()
        addTestImplementationDependency(call("files", list(
          PathManager.getJarPathForClass(FileComparisonFailure::class.java)!!,
          PathManager.getJarPathForClass(ComparisonFailure::class.java)!!
        )))
      }
    }
    test(gradleVersion, fixture) {
      val expectedPath = writeText("expected.txt", "Expected text.").path
      val actualPath = writeText("actual.txt", "Actual text.").path
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |
        |import com.intellij.rt.execution.junit.FileComparisonFailure;
        |import $jUnitTestAnnotationClass;
        |
        |public class TestCase {
        |
        |  @Test
        |  public void test_file_comparison_failure() {
        |    throw new FileComparisonFailure("assertion message", "Expected text.", "Actual text.", "$expectedPath", "$actualPath");
        |  }
        |
        |  @Test
        |  public void test_file_comparison_failure_without_actual_file() {
        |    throw new FileComparisonFailure("assertion message", "Expected text.", "Actual text.", "$expectedPath", null);
        |  }
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test_file_comparison_failure") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :Expected text.
              |Actual   :Actual text.
              |<Click to see difference>
              |
              |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message expected:<[Expected] text.> but was:<[Actual] text.>
            """.trimMargin())
            assertValue { testProxy ->
              val diffViewerProvider = testProxy.diffViewerProvider!!
              Assertions.assertEquals(expectedPath, diffViewerProvider.filePath)
              Assertions.assertEquals(actualPath, diffViewerProvider.actualFilePath)
            }
          }
          assertNode("test_file_comparison_failure_without_actual_file") {
            assertTestConsoleContains("""
              |
              |assertion message
              |Expected :Expected text.
              |Actual   :Actual text.
              |<Click to see difference>
              |
              |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message expected:<[Expected] text.> but was:<[Actual] text.>
            """.trimMargin())
            assertValue { testProxy ->
              val diffViewerProvider = testProxy.diffViewerProvider!!
              Assertions.assertEquals(expectedPath, diffViewerProvider.filePath)
              Assertions.assertEquals(null, diffViewerProvider.actualFilePath)
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("4.7+")
  @AllGradleVersionsSource
  fun `test not null assertion Junit5`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |
        |import org.junit.jupiter.api.Test;
        |import org.junit.jupiter.api.Assertions;
        |
        |public class TestCase {
        |
        |  @Test
        |  public void test_assert_null() {
        |    Assertions.assertNull("Actual text.");
        |  }
        |
        |  @Test
        |  public void test_assert_not_null() {
        |    Assertions.assertNotNull(null);
        |  }
        |}
      """.trimMargin())

      executeTasks(":test")
      assertTestTreeView {
        assertNode("TestCase") {
          assertNode("test_assert_null") {
            assertTestConsoleContains("""
              |
              |Expected :null
              |Actual   :Actual text.
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: expected: <null> but was: <Actual text.>
            """.trimMargin())
          }
          assertNode("test_assert_not_null") {
            assertTestConsoleContains("""
              |
              |org.opentest4j.AssertionFailedError: expected: not <null>
            """.trimMargin())
          }
        }
      }
    }
  }

  companion object {

    private val JAVA_JUNIT5_TESTS = """
      |package org.example;
      |
      |import org.junit.jupiter.api.Assertions;
      |import org.junit.jupiter.api.DisplayNameGeneration;
      |import org.junit.jupiter.api.DisplayNameGenerator;
      |import org.junit.jupiter.api.Test;
      |
      |@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    Assertions.assertEquals(5, 1 + 1, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    Assertions.assertEquals("expected text", "actual text", "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assertions.assertEquals(expected, actual, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assertions.assertEquals(expected, actual, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assertions.assertSame(expected, actual, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assertions.assertSame(expected, actual, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_multiple_assert_equals_for_texts() {
      |    Assertions.assertAll(
      |        () -> Assertions.assertEquals("expected text 1", "actual text 1", "assertion message 1"),
      |        () -> Assertions.assertEquals("expected text 2", "actual text 2", "assertion message 2"),
      |        () -> Assertions.assertEquals("expected text 3", "actual text 3", "assertion message 3")
      |    );
      |  }
      |}
    """.trimMargin()

    private val JAVA_ASSERTJ_JUNIT5_TESTS = """
      |package org.example;
      |
      |import org.assertj.core.api.Assertions;
      |import org.junit.jupiter.api.DisplayNameGeneration;
      |import org.junit.jupiter.api.DisplayNameGenerator;
      |import org.junit.jupiter.api.Test;
      |
      |@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    Assertions.assertThat(1 + 1)
      |        .isEqualTo(5);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    Assertions.assertThat("actual text")
      |        .isEqualTo("expected text");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assertions.assertThat(actual)
      |        .isEqualTo(expected);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assertions.assertThat(actual)
      |        .isEqualTo(expected);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assertions.assertThat(actual)
      |        .isSameAs(expected);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assertions.assertThat(actual)
      |        .isSameAs(expected);
      |  }
      |}
    """.trimMargin()

    private val JAVA_RAW_JUNIT5_TESTS = """
      |package org.example;
      |
      |import org.junit.jupiter.api.DisplayNameGeneration;
      |import org.junit.jupiter.api.DisplayNameGenerator;
      |import org.junit.jupiter.api.Test;
      |import org.opentest4j.AssertionFailedError;
      |
      |@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    throw new AssertionFailedError("assertion message", 5, 1 + 1);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    throw new AssertionFailedError("assertion message", "expected text", "actual text");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    throw new AssertionFailedError("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    throw new AssertionFailedError("assertion message", expected, actual);
      |  }
      |}
    """.trimMargin()

    private val JAVA_JUNIT4_TESTS = """
      |package org.example;
      |
      |import org.junit.Assert;
      |import org.junit.Test;
      |
      |@SuppressWarnings("deprecation")
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    Assert.assertEquals("assertion message", 5, 1 + 1);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    Assert.assertEquals("assertion message", "expected text", "actual text");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assert.assertEquals("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assert.assertEquals("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assert.assertSame("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assert.assertSame("assertion message", expected, actual);
      |  }
      |}
    """.trimMargin()

    private val JAVA_DEPRECATED_JUNIT4_TESTS = """
      |package org.example;
      |
      |import junit.framework.Assert;
      |import org.junit.Test;
      |
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    Assert.assertEquals("assertion message", 5, 1 + 1);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    Assert.assertEquals("assertion message", "expected text", "actual text");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assert.assertEquals("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assert.assertEquals("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assert.assertSame("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assert.assertSame("assertion message", expected, actual);
      |  }
      |}
    """.trimMargin()

    private val JAVA_TESTNG_TESTS = """
      |package org.example;
      |
      |import org.testng.Assert;
      |import org.testng.annotations.Test;
      |
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    Assert.assertEquals(1 + 1, 5, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    Assert.assertEquals("actual text", "expected text", "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assert.assertEquals(actual, expected, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assert.assertEquals(actual, expected, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    Assert.assertSame(actual, expected, "assertion message");
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Assert.assertSame(actual, expected, "assertion message");
      |  }
      |}
    """.trimMargin()

    private val JAVA_JUNIT_TESTNG_TESTS = """
      |package org.example;
      |
      |import org.testng.AssertJUnit;
      |import org.testng.annotations.Test;
      |
      |public class TestCase {
      |
      |  @Test
      |  public void test_assert_equals_for_ints() {
      |    AssertJUnit.assertEquals("assertion message", 5, 1 + 1);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_texts() {
      |    AssertJUnit.assertEquals("assertion message", "expected text", "actual text");
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    AssertJUnit.assertEquals("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_equals_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    AssertJUnit.assertEquals("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "expected text";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "actual text";
      |      }
      |    };
      |    AssertJUnit.assertSame("assertion message", expected, actual);
      |  }
      |
      |  @Test
      |  public void test_assert_same_for_same_objects() {
      |    Object expected = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    Object actual = new Object() {
      |      @Override
      |      public String toString() {
      |        return "string";
      |      }
      |    };
      |    AssertJUnit.assertSame("assertion message", expected, actual);
      |  }
      |}
    """.trimMargin()
  }
}