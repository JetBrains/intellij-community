// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestsUIUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.util.CONFIGURATION_CACHE_SUPPORTED_VERSIONS
import com.intellij.gradle.testFramework.util.JUNIT_5_SUPPORTED_VERSIONS
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewNode
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.consoleText
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.ProgressListener
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.testFramework.GradleTestExecutionTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.minutes

class GradleTestExecutionTest : GradleTestExecutionTestCase() {

  @TestDisposable
  private lateinit var testDisposable: Disposable

  @ParameterizedTest
  @TargetVersions(JUNIT_5_SUPPORTED_VERSIONS)
  @AllGradleVersionsSource
  fun `test grouping events of the same suite comes from different tasks`(gradleVersion: GradleVersion) {
    testJunitPlatformProject(gradleVersion) {
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
      assertBuildViewTree(buildView) {
        assertNode("failed") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewNode(buildView, "successful") {
        assertThat(it.consoleText).contains("buildscript \noutput\n\ntext")
        assertThat(it.consoleText).contains("script output text without eol")
      }
      assertTestConsoleDoesNotContain("script output text without eol")
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(runView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(runView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("failed") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNode("Unknown command-line option '--tests'")
        }
      }

      executeTasks(":allTests --tests *", isRunAsTest = false)
      assertBuildViewTree(runView) {
        assertNode("failed") {
          assertNode("Unknown command-line option '--tests'")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("<7.6", reason = "IDEA-340676 flaky test")
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
  @TargetVersions(CONFIGURATION_CACHE_SUPPORTED_VERSIONS)
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

      assertBuildViewNode(buildView, "successful") {
        assertThat(it.consoleText).contains("Green light to resolve configuration")
        assertThat(it.consoleText).contains("Attempt to resolve configuration")
      }

      assertTestConsoleDoesNotContain("Green light to resolve configuration")
      assertTestConsoleDoesNotContain("Attempt to resolve configuration")
      assertTestConsoleDoesNotContain("Attempt to resolve configuration too early")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions(JUNIT_5_SUPPORTED_VERSIONS)
  fun `test test task execution with additional gradle listeners`(gradleVersion: GradleVersion) {
    val extension = object : GradleExecutionHelperExtension {
      override fun configureOperation(operation: LongRunningOperation, context: GradleExecutionContext) {
        operation.addProgressListener(ProgressListener {})
      }
    }
    testJunitPlatformProject(gradleVersion) {
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
      assertBuildViewTree(buildView) {
        assertNode("successful") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
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
  @TargetVersions("7.5+")
  fun `test Gradle test distribution nodes are hidden by default`(gradleVersion: GradleVersion) {
    testJunitPlatformProject(gradleVersion) {
      // Project configuration without an existing directory is not allowed
      runBlocking {
        edtWriteAction {
          projectRoot.findOrCreateDirectory("lib")
        }
      }
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
      assertBuildViewNode(buildView, "successful") {
        assertThat(it.consoleText).contains("The max parallel fork was [$parallelForks]")
      }
      assertTestConsoleDoesNotContain("The max parallel fork was [$parallelForks]")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test single completion notification for passed test and rerun`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      for (commandLine in listOf(":test", ":test --rerun-tasks")) {
        val completion = executeTestsAndAwaitCompletion(commandLine)
        assertTestRunnerCompletion(completion, exitCode = 0, isDefect = false)
        assertTestViewTree {
          assertNode("TestCase") {
            assertNode("test")
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test single completion notification for failed test`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {
        |    throw new RuntimeException("Test error message");
        |  }
        |}
      """.trimMargin())

      val completion = executeTestsAndAwaitCompletion(":test")
      assertTestRunnerCompletion(completion, exitCode = 1, isDefect = true)
      assertTestViewTree {
        assertNode("TestCase") {
          assertNode("test")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test failed root at completion when compilation fails`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      writeText("src/main/java/org/example/App.java", """
        |package org.example;
        |public class App {
        |  compilation error
        |}
      """.trimMargin())
      writeText("src/test/java/org/example/TestCase.java", """
        |package org.example;
        |import $jUnitTestAnnotationClass;
        |public class TestCase {
        |  @Test public void test() {}
        |}
      """.trimMargin())

      val completion = executeTestsAndAwaitCompletion(":test")
      assertTestRunnerCompletion(completion, exitCode = 1, isDefect = true)
      assertTestViewTreeIsEmpty()
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test single completion notification without test sources`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      val completion = executeTestsAndAwaitCompletion(":test")
      assertTestRunnerCompletion(completion, exitCode = 0, isDefect = false)
      assertTestViewTreeIsEmpty()
    }
  }

  private fun executeTestsAndAwaitCompletion(commandLine: String): TestRunnerCompletion {
    val notifications = ConcurrentLinkedQueue<Notification>()
    val rootStates = ConcurrentLinkedQueue<TestRootState>()
    val exitCodes = ConcurrentLinkedQueue<Int>()
    val connection = project.messageBus.connect(testDisposable)
    connection.subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == TestsUIUtil.getNotificationGroup().displayId) {
          notifications.add(notification)
        }
      }
    })
    connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, object : SMTRunnerEventsAdapter() {
      override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
        rootStates.add(TestRootState(testsRoot.isInProgress, testsRoot.isDefect))
      }
    })
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        exitCodes.add(exitCode)
      }
    })
    timeoutRunBlocking(EXECUTION_TIMEOUT) {
      executeTasksAsync(commandLine, isRunAsTest = true)
      waitUntil("The test runner didn't complete the test execution") {
        notifications.isNotEmpty() && rootStates.isNotEmpty() && exitCodes.isNotEmpty()
      }
    }
    // A duplicate notification can be still in the EDT queue.
    invokeAndWaitIfNeeded {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    return TestRunnerCompletion(notifications.map { it.content }, rootStates.toList(), exitCodes.toList())
  }

  private fun assertTestRunnerCompletion(completion: TestRunnerCompletion, exitCode: Int, isDefect: Boolean) {
    Assertions.assertEquals(listOf(exitCode), completion.exitCodes)
    Assertions.assertEquals(listOf(TestRootState(isInProgress = false, isDefect = isDefect)), completion.rootStates)
    Assertions.assertEquals(1, completion.notifications.size) {
      "Expected one test runner notification, but got ${completion.notifications}"
    }
  }

  private class TestRunnerCompletion(
    val notifications: List<String>,
    val rootStates: List<TestRootState>,
    val exitCodes: List<Int>,
  )

  /**
   * The state of the test root at the moment of the [SMTRunnerEventsListener.onTestingFinished] event.
   */
  private data class TestRootState(val isInProgress: Boolean, val isDefect: Boolean)

  companion object {
    private val EXECUTION_TIMEOUT = 1.minutes
  }
}
