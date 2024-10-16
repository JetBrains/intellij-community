// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.use
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatConfigurationCacheIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest

class GradleTestExecutionTest : GradleExecutionTestCase() {

  @ParameterizedTest
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

      executeTasks(":test :additionalTest --continue", isRunAsTest = true)
      assertTestViewTree {
        assertNode("AppTest") {
          assertNode("test")
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("failed") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(AppTest|Test class org.example.AppTest)".toRegex()) {
                assertNode("Test test()(org.example.AppTest)")
              }
            }
          }
          assertNode(":additionalTest") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(AppTest|Test class org.example.AppTest)".toRegex()) {
                assertNode("Test test()(org.example.AppTest)") {
                  assertNode("'Test error message'")
                }
              }
            }
            // IDEA-334636 this node should have the same indention as the 'additionalTest' node
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
      assertTestViewTree {
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
      assertTestViewTree {
        assertNode("AppTest") {
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(AppTest|Test class org.example.AppTest)".toRegex()) {
                assertNode("Test test()(org.example.AppTest)")
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
      assertTestViewTree {
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
      assertTestViewTree {
        assertNode("TestCase1") {
          assertNode("test1")
          assertNode("test2")
        }
      }

      executeTasks(":test --tests org.example.TestCase2.test2", isRunAsTest = true)
      assertTestViewTree {
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
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(TestCase|Test class org.example.TestCase)".toRegex()) {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
        }
      }

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(TestCase|Test class org.example.TestCase)".toRegex()) {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
        }
      }

      executeTasks(":test --rerun-tasks", isRunAsTest = false)
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(TestCase|Test class org.example.TestCase)".toRegex()) {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
        }
      }

      executeTasks(":test", isRunAsTest = false)
      assertTestViewTreeIsEmpty()
      assertBuildViewTree {
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
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(TestCase|Test class org.example.TestCase)".toRegex()) {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
          assertNode(":allTests")
        }
      }

      executeTasks(":allTests", isRunAsTest = true)
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(TestCase|Test class org.example.TestCase)".toRegex()) {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
          assertNode(":allTests")
        }
      }

      executeTasks(":allTests --rerun-tasks", isRunAsTest = false)
      assertRunViewTree {
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
      assertRunViewTree {
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
      assertTestViewTreeIsEmpty()
      assertBuildViewTree {
        assertNode("failed") {
          assertNode("Unknown command-line option '--tests'")
        }
      }

      executeTasks(":allTests --tests *", isRunAsTest = false)
      assertRunViewTree {
        assertNode("failed") {
          assertNode("Unknown command-line option '--tests'")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task execution order`(gradleVersion: GradleVersion) {
    assumeThatGradleIsOlderThan(gradleVersion, "7.6"){
      "IDEA-340676 flaky test"
    }
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
      assertBuildViewTree {
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
              assertNode("TestCase") {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
        }
      }
      executeTasks(":test --tests org.example.TestCase.test :afterTest", isRunAsTest = true)
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              assertNode("TestCase") {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
          assertNode(":afterTest")
        }
      }
      executeTasks(":beforeTest :test --tests org.example.TestCase.test :afterTest", isRunAsTest = true)
      assertBuildViewTree {
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
              assertNode("TestCase") {
                assertNode("Test test()(org.example.TestCase)")
              }
            }
          }
          assertNode(":afterTest")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test configuration cache for tests`(gradleVersion: GradleVersion) {
    assumeThatConfigurationCacheIsSupported(gradleVersion)
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
      assertTestViewTree { assertNode("TestCase") { assertNode("test1"); assertNode("test2") } }
      assertTestConsoleDoesNotContain("Unable to enhance Gradle Daemon")

      executeTasks(":test --tests org.example.TestCase.test1", isRunAsTest = true)
      assertTestViewTree { assertNode("TestCase") { assertNode("test1") } }
      assertTestConsoleDoesNotContain("Unable to enhance Gradle Daemon")

      executeTasks(":test --tests org.example.TestCase.test2", isRunAsTest = true)
      assertTestViewTree { assertNode("TestCase") { assertNode("test2") } }
      assertTestConsoleDoesNotContain("Unable to enhance Gradle Daemon")
    }
  }

  @ParameterizedTest
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

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test test task execution with additional gradle listeners`(gradleVersion: GradleVersion) {
    val extension = object : GradleExecutionHelperExtension {
      override fun prepareForExecution(id: ExternalSystemTaskId,
                                       operation: LongRunningOperation,
                                       settings: GradleExecutionSettings,
                                       buildEnvironment: BuildEnvironment?) {
        operation.addProgressListener(ProgressListener {})
      }
    }
    testJunit5Project(gradleVersion) {
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |   @Test public void test1() {}
        |}
      """.trimMargin())
      Disposer.newDisposable().use { testDisposable ->
        GradleExecutionHelperExtension.EP_NAME.point.registerExtension(extension, testDisposable)
        executeTasks(":test", isRunAsTest = true)
      }
      assertTestViewTree {
        assertNode("AppTest") {
          assertNode("test1")
        }
      }
      assertBuildViewTree {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":compileTestJava")
          assertNode(":processTestResources")
          assertNode(":testClasses")
          assertNode(":test") {
            if (isBuiltInTestEventsUsed()) {
              // temporary solution until IDEA-354299
              assertNode("(AppTest|Test class org.example.AppTest)".toRegex()) {
                assertNode("Test test1()(org.example.AppTest)")
              }
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test Gradle test distribution nodes are hidden by default`(gradleVersion: GradleVersion) {
    assumeThatGradleIsAtLeast(gradleVersion, "7.5")
    testJunit5Project(gradleVersion) {
      writeText("settings.gradle", """
        plugins {
            id "com.gradle.enterprise" version "3.15.1"
        }

        gradleEnterprise {
            server = "https://something.com"
        }

        rootProject.name = '${project.name}'
        include('lib')
      """.trimIndent())
      appendText("build.gradle", """
        tasks.withType(Test).configureEach() {
          distribution {
              enabled = true
              remoteExecutionPreferred = false
              maxRemoteExecutors = 0
          }
        }
      """.trimMargin())
      writeText("src/test/java/org/example/AppTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class AppTest {
        |   @Test public void testApp() {}
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/LibTest.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class LibTest {
        |   @Test public void testLib() {}
        |}
      """.trimMargin())

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("Distributed Test Run :test") {
          assertNode("AppTest") {
            assertNode("testApp")
          }
          assertNode("LibTest") {
            assertNode("testLib")
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test maxParallelFork option is not reset for test executions`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      // we need the presence of some test sources
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test1() {}
        |}
      """.trimMargin())
      // configure parallel forks value
      val parallelForks = 5
      // output max parallel forks value after test execution
      appendText("build.gradle", """
        |test {
        |    maxParallelForks = $parallelForks
        |    doLast { Test t ->
        |        logger.lifecycle("The max parallel fork was [${'$'}{t.maxParallelForks}]")
        |    }
        |}
      """.trimMargin())
      executeTasks(":test", isRunAsTest = true)
      // verify the max parallel fork value did not change
      assertTestConsoleContains("The max parallel fork was [$parallelForks]")
    }
  }
}
