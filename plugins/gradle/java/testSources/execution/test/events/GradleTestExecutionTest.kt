// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.openapi.util.SystemInfo
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest

class GradleTestExecutionTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @TargetVersions("4.7+")
  @AllGradleVersionsSource
  fun `test grouping events of the same suite comes from different tasks`(gradleVersion: GradleVersion) {
    testJunit5Project(gradleVersion) {
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

      executeTasks(":test :additionalTest", isRunAsTest = true)
      assertTestTreeView {
        assertNode("AppTest") {
          assertNode("test")
          assertNode("test")
        }
      }
      assertBuildExecutionTree {
        assertNode("failed") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("AppTest") {
                    assertNode("Test test()(org.example.AppTest)")
                  }
                }
              }
            }
          }
          assertNode(":additionalTest") {
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :additionalTest") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("AppTest") {
                    assertNode("Test test()(org.example.AppTest)") {
                      assertNode("'Test error message'")
                    }
                  }
                }
              }
            }
            assertNode("There were failing tests. See the report at: .*".toRegex())
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

      executeTasks(":test", isRunAsTest = true)
      assertTestTreeView {
        assertNode("AppTest") {
          assertNode("test")
        }
      }
      when {
        SystemInfo.isWindows ->
          assertTestConsoleContains("buildscript \n" + "output\n" + "\n" + "text\n")
        else ->
          assertTestConsoleContains("buildscript \n" + "output\n" + "text\n")
      }
      assertTestConsoleContains("script output text without eol")
      assertTestConsoleContains("test \n" + "output\n" + "\n" + "text\n")
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

      executeTasks(":test", isRunAsTest = true)
      assertTestTreeView {
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
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
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
  fun `test task execution with filters`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase1.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase1 {
        |  @Test public void test1() {}
        |  @Test public void test2() {}
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/TestCase2.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase2 {
        |  @Test public void test1() {}
        |  @Test public void test2() {}
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase1") {
          assertNode("test1")
          assertNode("test2")
        }
        assertNode("TestCase2") {
          assertNode("test1")
          assertNode("test2")
        }
      }

      executeTasks(":test --tests org.example.TestCase1", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase1") {
          assertNode("test1")
          assertNode("test2")
        }
      }

      executeTasks(":test --tests org.example.TestCase2.test2", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase2") {
          assertNode("test2")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test test task execution`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase") {
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
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
        }
      }

      executeTasks(":test", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase") {
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
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
        }
      }

      executeTasks(":test --rerun-tasks", isRunAsTest = false)
      assertTestTreeView {
        assertNode("TestCase") {
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
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
        }
      }

      executeTasks(":test", isRunAsTest = false)
      assertTestTreeViewIsEmpty()
      assertBuildExecutionTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test non test task execution`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |tasks.create('allTests') {
        |    dependsOn(tasks.findByPath(':test'))
        |}
      """.trimMargin())

      executeTasks(":allTests", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase") {
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
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
          assertNode(":allTests")
        }
      }

      executeTasks(":allTests", isRunAsTest = true)
      assertTestTreeView {
        assertNode("TestCase") {
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
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
          assertNode(":allTests")
        }
      }

      executeTasks(":allTests --rerun-tasks", isRunAsTest = false)
      assertRunTreeView {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test")
          assertNode(":allTests")
        }
      }

      executeTasks(":allTests", isRunAsTest = false)
      assertRunTreeView {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test")
          assertNode(":allTests")
        }
      }
    }
  }

  /**
   * Disabled due to deprecation cycle of --tests option for non-test tasks.
   * Please see IDEA-318304 for details.
   * Please remove org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.fixUpGradleCommandLine,
   */
  @Disabled
  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test hacky non-test task execution`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |tasks.create('allTests') {
        |    dependsOn(tasks.findByPath(':test'))
        |}
      """.trimMargin())

      executeTasks(":allTests --tests *", isRunAsTest = true)
      assertTestTreeViewIsEmpty()
      assertBuildExecutionTree {
        assertNode("failed") {
          assertNode("Unknown command-line option '--tests'")
        }
      }

      executeTasks(":allTests --tests *", isRunAsTest = false)
      assertRunTreeView {
        assertNode("failed") {
          assertNode("Unknown command-line option '--tests'")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task execution order`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())
      appendText("build.gradle", """
        |tasks.create('beforeTest')
        |tasks.create('afterTest')
      """.trimMargin())

      executeTasks(":beforeTest :test --tests org.example.TestCase.test", isRunAsTest = true)
      assertBuildExecutionTree {
        assertNode("successful") {
          assertNode(":beforeTest")
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
        }
      }
      executeTasks(":test --tests org.example.TestCase.test :afterTest", isRunAsTest = true)
      assertBuildExecutionTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
          assertNode(":afterTest")
        }
      }
      executeTasks(":beforeTest :test --tests org.example.TestCase.test :afterTest", isRunAsTest = true)
      assertBuildExecutionTree {
        assertNode("successful") {
          assertNode(":beforeTest")
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              assertNode("Gradle Test Run :test") {
                assertNode("Gradle Test Executor \\d+".toRegex()) {
                  assertNode("TestCase") {
                    assertNode("Test test()(org.example.TestCase)")
                  }
                }
              }
            }
          }
          assertNode(":afterTest")
        }
      }
    }
  }

  @ParameterizedTest
  @TargetVersions("8.1+")
  @GradleTestSource("8.1")
  fun `test configuration cache for tests`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test1() {}
        |  @Test public void test2() {}
        |}
      """.trimMargin())
      writeText("gradle.properties", """
        |org.gradle.configuration-cache=true
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestTreeView { assertNode("TestCase") { assertNode("test1"); assertNode("test2") } }
      assertTestConsoleDoesNotContain("Unable to enhance Gradle Daemon")

      executeTasks(":test --tests org.example.TestCase.test1", isRunAsTest = true)
      assertTestTreeView { assertNode("TestCase") { assertNode("test1") } }
      assertTestConsoleDoesNotContain("Unable to enhance Gradle Daemon")

      executeTasks(":test --tests org.example.TestCase.test2", isRunAsTest = true)
      assertTestTreeView { assertNode("TestCase") { assertNode("test2") } }
      assertTestConsoleDoesNotContain("Unable to enhance Gradle Daemon")
    }
  }

  @ParameterizedTest
  @TargetVersions("3.5+")
  @AllGradleVersionsSource
  fun `test configuration resolves after execution graph`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      appendText("build.gradle", """
        |import java.util.concurrent.atomic.AtomicBoolean;
        |
        |def resolutionAllowed = new AtomicBoolean(false)
        |
        |configurations.testRuntimeClasspath.incoming.beforeResolve {
        |  logger.warn("Attempt to resolve configuration")
        |  if (!resolutionAllowed.get()) {
        |    logger.warn("Attempt to resolve configuration too early")
        |  }
        |}
        |
        |gradle.taskGraph.beforeTask { Task task ->
        |  if (task.path == ":test" ) {
        |    logger.warn("Green light to resolve configuration")
        |    resolutionAllowed.set(true)
        |  }
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestConsoleContains("Green light to resolve configuration")
      assertTestConsoleContains("Attempt to resolve configuration")
      assertTestConsoleDoesNotContain("Attempt to resolve configuration too early")
    }
  }
}
