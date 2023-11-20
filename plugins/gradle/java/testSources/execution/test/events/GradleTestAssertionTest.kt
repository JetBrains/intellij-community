// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.openapi.application.PathManager
import com.intellij.rt.execution.junit.FileComparisonFailure
import junit.framework.ComparisonFailure
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatJunit5IsSupported
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestAssertionTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Junit 5`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
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
        |  
        |  @Test
        |  public void test_wrapped_assertion_exception() {
        |    try {
        |      Assertions.assertEquals("expected text", "actual text", "assertion message");
        |    } catch (AssertionError error) {
        |      throw new AssertionError("additional message", error);
        |    }
        |  }
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

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
            if (isBuiltInTestEventsUsed()) {
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
          assertNode("test wrapped assertion exception") {
            // Wrapped assertion exceptions aren't recognized by Gradle and IDE comparison extractors.
            assertTestConsoleContains("""
              |java.lang.AssertionError: additional message
            """.trimMargin())
            assertTestConsoleContains("""
              |org.opentest4j.AssertionFailedError: assertion message ==> expected: <expected text> but was: <actual text>
            """.trimMargin())
          }
          assertNode("test assert null") {
            assertTestConsoleContains("""
              |
              |Expected :null
              |Actual   :Actual text.
              |<Click to see difference>
              |
              |org.opentest4j.AssertionFailedError: expected: <null> but was: <Actual text.>
            """.trimMargin())
          }
          assertNode("test assert not null") {
            assertTestConsoleDoesNotContain("""
              |Expected :
              |Actual   :
              |<Click to see difference>
            """.trimMargin())
            assertTestConsoleContains("""
              |
              |org.opentest4j.AssertionFailedError: expected: not <null>
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Junit 5 (Opentest4j)`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
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
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
            if (isBuiltInTestEventsUsed() && !isTestLauncherUsed()) {
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
            else {
              // Test output has object hashes, it is hard to predict and assert
              assertTestConsoleContains("""
                |
                |org.opentest4j.AssertionFailedError: assertion message
              """.trimMargin())
            }
          }
          assertNode("test assert equals for same objects") {
            if (isBuiltInTestEventsUsed() && !isTestLauncherUsed()) {
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
            else {
              // Test output has object hashes, it is hard to predict and assert
              assertTestConsoleContains("""
                |
                |org.opentest4j.AssertionFailedError: assertion message
              """.trimMargin())
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Junit 5 (AssertJ)`(gradleVersion: GradleVersion) {
    testJunit5AssertJProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
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
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
      writeText("src/test/java/org/example/TestCase.java", """
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
        |
        |  @Test
        |  public void test_wrapped_assertion_exception() {
        |    try {
        |      Assert.assertEquals("assertion message", "expected text", "actual text");
        |    } catch (AssertionError error) {
        |      throw new AssertionError("additional message", error);
        |    }
        |  }
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
          assertNode("test_wrapped_assertion_exception") {
            // Wrapped assertion exceptions aren't recognized by Gradle and IDE comparison extractors.
            assertTestConsoleContains("""
              |java.lang.AssertionError: additional message
            """.trimMargin())
            assertTestConsoleContains("""
              |org.junit.ComparisonFailure: assertion message expected:<[expected] text> but was:<[actual] text>
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Junit 4 (Junit 3)`(gradleVersion: GradleVersion) {
    testJunit4Project(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
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
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
      writeText("src/test/java/org/example/TestCase.java", """
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
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
  fun `test assertion result of Test NG (Junit 3)`(gradleVersion: GradleVersion) {
    testTestNGProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
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
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
  fun `test assertion result of Junit 4 (IJ FileComparisonFailure)`(gradleVersion: GradleVersion) {
    val fixture = GradleTestFixtureBuilder.create("GradleTestAssertionTest-file-comparison-junit-4") {
      withSettingsFile {
        setProjectName("GradleTestAssertionTest-file-comparison-junit-4")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit4()
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
        |import org.junit.Test;
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
        |
        |  @Test
        |  public void test_wrapped_file_comparison_failure() {
        |    try {
        |      throw new FileComparisonFailure("assertion message", "Expected text.", "Actual text.", "$expectedPath", "$actualPath");
        |    } catch (AssertionError error) {
        |      throw new AssertionError("additional message", error);
        |    }
        |  }
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
          assertNode("test_wrapped_file_comparison_failure") {
            // Wrapped assertion exceptions aren't recognized by Gradle and IDE comparison extractors.
            assertTestConsoleContains("""
              |java.lang.AssertionError: additional message
            """.trimMargin())
            assertTestConsoleContains("""
              |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message expected:<[Expected] text.> but was:<[Actual] text.>
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test assertion result of Junit 5 (IJ FileComparisonFailure)`(gradleVersion: GradleVersion) {
    assumeThatJunit5IsSupported(gradleVersion)
    val fixture = GradleTestFixtureBuilder.create("GradleTestAssertionTest-file-comparison-junit-5") {
      withSettingsFile {
        setProjectName("GradleTestAssertionTest-file-comparison-junit-5")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit5()
        addTestImplementationDependency(call("files", list(
          PathManager.getJarPathForClass(FileComparisonFailure::class.java)!!,
          PathManager.getJarPathForClass(ComparisonFailure::class.java)!!
        )))
      }
    }
    test(gradleVersion, fixture) {
      val expectedPath = writeText("expected.txt", "Expected text.").path
      val actualPath = writeText("actual.txt", "Actual text.").path
      val expectedPath1 = writeText("expected1.txt", "Expected text 1.").path
      val actualPath1 = writeText("actual1.txt", "Actual text 1.").path
      val expectedPath2 = writeText("expected2.txt", "Expected text 2.").path
      val actualPath2 = writeText("actual2.txt", "Actual text 2.").path
      val expectedPath3 = writeText("expected3.txt", "Expected text 3.").path
      val actualPath3 = writeText("actual3.txt", "Actual text 3.").path
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |
        |import com.intellij.rt.execution.junit.FileComparisonFailure;
        |import org.junit.jupiter.api.Assertions;
        |import org.junit.jupiter.api.Test;
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
        |
        |  @Test
        |  public void test_multiple_file_comparison_failure() {
        |    Assertions.assertAll(
        |      () -> { throw new FileComparisonFailure("assertion message 1", "Expected text 1.", "Actual text 1.", "$expectedPath1", "$actualPath1"); },
        |      () -> { throw new FileComparisonFailure("assertion message 2", "Expected text 2.", "Actual text 2.", "$expectedPath2", "$actualPath2"); },
        |      () -> { throw new FileComparisonFailure("assertion message 3", "Expected text 3.", "Actual text 3.", "$expectedPath3", "$actualPath3"); }
        |    );
        |  }
        |
        |  @Test
        |  public void test_wrapped_file_comparison_failure() {
        |    try {
        |      throw new FileComparisonFailure("assertion message", "Expected text.", "Actual text.", "$expectedPath", "$actualPath");
        |    } catch (AssertionError error) {
        |      throw new AssertionError("additional message", error);
        |    }
        |  }
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
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
          assertNode("test_multiple_file_comparison_failure") {
            if (isBuiltInTestEventsUsed()) {
              assertTestConsoleContains("""
                |
                |assertion message 1
                |Expected :Expected text 1.
                |Actual   :Actual text 1.
                |<Click to see difference>
                |
                |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message 1 expected:<[Expected] text 1.> but was:<[Actual] text 1.>
              """.trimMargin())
              assertTestConsoleContains("""
                |
                |assertion message 2
                |Expected :Expected text 2.
                |Actual   :Actual text 2.
                |<Click to see difference>
                |
                |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message 2 expected:<[Expected] text 2.> but was:<[Actual] text 2.>
              """.trimMargin())
              assertTestConsoleContains("""
                |
                |assertion message 3
                |Expected :Expected text 3.
                |Actual   :Actual text 3.
                |<Click to see difference>
                |
                |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message 3 expected:<[Expected] text 3.> but was:<[Actual] text 3.>
              """.trimMargin())
            } else {
              assertTestConsoleContains("""
                |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message 1 expected:<[Expected] text 1.> but was:<[Actual] text 1.>
              """.trimMargin())
              assertTestConsoleContains("""
                |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message 2 expected:<[Expected] text 2.> but was:<[Actual] text 2.>
              """.trimMargin())
              assertTestConsoleContains("""
                |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message 3 expected:<[Expected] text 3.> but was:<[Actual] text 3.>
              """.trimMargin())
            }
          }
          assertNode("test_wrapped_file_comparison_failure") {
            // Wrapped assertion exceptions aren't recognized by Gradle and IDE comparison extractors.
            assertTestConsoleContains("""
              |java.lang.AssertionError: additional message
            """.trimMargin())
            assertTestConsoleContains("""
              |com.intellij.rt.execution.junit.FileComparisonFailure: assertion message expected:<[Expected] text.> but was:<[Actual] text.>
            """.trimMargin())
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test controlled spread of Gradle daemon's pollution`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |import java.nio.file.Paths
        |
        |test {
        |  doLast {
        |    def daemonMainClass = Class.forName("org.gradle.launcher.daemon.bootstrap.DaemonMain")
        |    def classLoader = (URLClassLoader) daemonMainClass.getClassLoader()
        |    for (def url: classLoader.getURLs()) {
        |       println 'DAEMON_CLASSPATH: ' + Paths.get(url.toURI())
        |    }
        |    for (def file: test.classpath.files) {
        |       println 'TEST_CLASSPATH: ' + Paths.get(file.toURI())
        |    }
        |  }
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestConsoleDoesNotContain("DAEMON_CLASSPATH: " + projectRoot.toNioPath())
      assertTestConsoleContains("TEST_CLASSPATH: " + projectRoot.toNioPath())
    }
  }
}
