// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.openapi.util.SystemInfo
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class GradleTestExecutionTest : GradleTestExecutionTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7+")
  @AllGradleVersionsSource
  fun `test grouping events of the same suite comes from different tasks`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |  @Test
        |  public void test() {
        |    String prop = System.getProperty("prop");
        |    if (prop != null) {
        |      throw new RuntimeException(prop);
        |    }
        |  }
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |tasks.register('additionalTest', Test) {
        |  testClassesDirs = sourceSets.test.output.classesDirs
        |  classpath = sourceSets.test.runtimeClasspath
        |  jvmArgs += "-Dprop='Test error message'"
        |
        |  useJUnitPlatform()
        |}
      """.trimMargin())

      executeTasks(":test :additionalTest")

      assertTestExecutionTree {
        assertNode("AppTest") {
          assertNode("test")
          assertNode("test")
        }
      }
      assertBuildExecutionTreeContains {
        assertNode("failed") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isSupportedTestLauncher()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor 1") {
                  assertNode("AppTest") {
                    assertNode("Test test()(org.example.AppTest)")
                  }
                }
              }
            }
          }
          assertNode(":additionalTest") {
            if (isSupportedTestLauncher()) {
              assertNode("Gradle Test Run :additionalTest") {
                assertNode("Gradle Test Executor 2") {
                  assertNode("AppTest") {
                    assertNode("Test test()(org.example.AppTest)") {
                      assertNode("'Test error message'")
                    }
                  }
                }
              }
            }
            else {
              assertNode("There were failing tests. See the report at:")
            }
          }
          if (isSupportedTestLauncher()) {
            assertNode("Test failed.")
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test console empty lines and output without eol at the end`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |  @Test
        |  public void test() {
        |    System.out.println("test \n" + "output\n" + "\n" + "text");
        |  }
        |}
      """.trimMargin())
      prependText("build.gradle", """
        |buildscript {
        |  print("buildscript \n" + "output\n" + "\n" + "text\n")
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |print("script output text without eol")
      """.trimMargin())

      executeTasks(":test")

      assertTestExecutionTree {
        assertNode("AppTest") {
          assertNode("test")
        }
      }
      when {
        SystemInfo.isWindows ->
          assertTestExecutionConsoleContains("buildscript \n" + "output\n" + "\n" + "text\n")
        else ->
          assertTestExecutionConsoleContains("buildscript \n" + "output\n" + "text\n")
      }
      assertTestExecutionConsoleContains("script output text without eol")
      assertTestExecutionConsoleContains("test \n" + "output\n" + "\n" + "text\n")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test build tw output for Gradle test runner execution`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |    @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":test")

      assertTestExecutionTree {
        assertNode("AppTest") {
          assertNode("test")
        }
      }
      assertBuildExecutionTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isSupportedTestLauncher()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor 1") {
                  assertNode("AppTest") {
                    assertNode("Test test()(org.example.AppTest)")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test test execution status`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |
        |public class TestCase {
        |
        |  @$jUnitTestAnnotationClass
        |  public void successTest() {}
        |
        |  @$jUnitTestAnnotationClass
        |  public void failedTest() { 
        |    throw new RuntimeException(); 
        |  }
        |
        |  @$jUnitIgnoreAnnotationClass
        |  @$jUnitTestAnnotationClass
        |  public void ignoredTest() {}
        |}
      """.trimMargin())

      executeTasks(":test")

      testEventCounter.assertTestEvents("TestCase", 1, 1, 0, 0, 0, 0)
      testEventCounter.assertTestEvents("successTest", 0, 0, 1, 1, 0, 0)
      testEventCounter.assertTestEvents("failedTest", 0, 0, 1, 1, 1, 0)
      testEventCounter.assertTestEvents("ignoredTest", 0, 0, 1, 1, 0, 1)
    }
  }
}
