// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.gradle.testFramework.GradleExecutionTestCase
import com.intellij.gradle.testFramework.annotations.AllGradleVersionsSource
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewNode
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.assertion.consoleText
import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.junit.jupiter.params.ParameterizedTest

/** Runs [action] with the `gradle.build.toolwindow.single.console` registry flag set to [enabled], restoring the previous value afterwards. */
internal inline fun withSingleConsole(enabled: Boolean, action: () -> Unit) {
  val registryValue = Registry.get("gradle.build.toolwindow.single.console")
  val previousValue = registryValue.asString()
  registryValue.setValue(enabled)
  try {
    action()
  }
  finally {
    registryValue.setValue(previousValue)
  }
}

class GradleExecutionOutputTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("true, false")
  fun `test task execution output without failures`(gradleVersion: GradleVersion, singleConsole: Boolean) =
    withSingleConsole(singleConsole) {
      testJavaProject(gradleVersion) {
        writeText("build.gradle", buildScript(gradleVersion, GradleDsl.GROOVY) {
          registerTask("task") {
            call("doLast") {
              call("println", "Task doLast")
            }
          }
        })

        executeTasks(":task")
        assertBuildViewTree(runView) {
          assertNode("successful") {
            assertNodeWithDeprecatedGradleWarning(gradleVersion)
            assertNode(":task")
          }
        }
        assertBuildViewNode(runView, "successful") {
          Assertions.assertThat(it.consoleText)
            .containsOnlyOnce("Task doLast")
        }
        assertBuildViewNode(runView, ":task") {
          if (isPerTaskOutputSupported()) {
            Assertions.assertThat(it.consoleText)
              .containsOnlyOnce("Task doLast")
          }
        }
      }
    }

  @ParameterizedTest
  @AllGradleVersionsSource("true, false")
  fun `test task execution output with one failure`(gradleVersion: GradleVersion, singleConsole: Boolean) =
    withSingleConsole(singleConsole) {
      testJavaProject(gradleVersion) {
        writeText("build.gradle", buildScript(gradleVersion, GradleDsl.GROOVY) {
          registerTask("failingTask") {
            call("doLast") {
              call("println", "Task doLast")
              code("throw new Exception('Task failure')")
            }
          }
        })

        executeTasks(":failingTask")
        val failureTitle = when (isGradleAtLeast("9.5")) {
          true -> "Execution failed for task ':failingTask' (registered in build file 'build.gradle')."
          else -> "Execution failed for task ':failingTask'."
        }
        assertBuildViewTree(runView) {
          assertNode("failed") {
            assertNodeWithDeprecatedGradleWarning(gradleVersion)
            assertNode(":failingTask")
            assertNode("FAILURE: Build failed with an exception.") {
              assertNode("build.gradle") {
                assertNode(failureTitle)
              }
            }
          }
        }
        assertBuildViewNode(runView, "failed") {
          Assertions.assertThat(it.consoleText)
            .containsOnlyOnce("Task doLast")
            .contains("Task failure")
        }
        assertBuildViewNode(runView, ":failingTask") {
          if (isPerTaskOutputSupported()) {
            Assertions.assertThat(it.consoleText)
              .containsOnlyOnce("Task doLast")
            //.contains("Task failure")
          }
        }
        assertBuildViewNode(runView, failureTitle) {
          if (isPerTaskOutputSupported() && !singleConsole) {
            Assertions.assertThat(it.consoleText)
              .contains("Task failure")
          }
        }
      }
    }

  @ParameterizedTest
  @AllGradleVersionsSource("true, false")
  fun `test task execution output with two failures`(gradleVersion: GradleVersion, singleConsole: Boolean) =
    withSingleConsole(singleConsole) {
      testJavaProject(gradleVersion) {
        writeText("build.gradle", buildScript(gradleVersion, GradleDsl.GROOVY) {
          registerTask("failingTask1") {
            call("doLast") {
              call("println", "Task 1 doLast")
              code("throw new Exception('Task 1 failure')")
            }
          }
          registerTask("failingTask2") {
            call("doLast") {
              call("println", "Task 2 doLast")
              code("throw new Exception('Task 2 failure')")
            }
          }
          registerTask("failingTasksGroup") {
            call("dependsOn", "failingTask1", "failingTask2")
          }
        })

        executeTasks(":failingTasksGroup --continue")

        val failureSuffix = when (isGradleAtLeast("9.5")) {
          true -> " (registered in build file 'build.gradle')."
          else -> "."
        }
        assertBuildViewTree(runView) {
          assertNode("failed") {
            assertNodeWithDeprecatedGradleWarning(gradleVersion)
            assertNode(":failingTask1")
            assertNode(":failingTask2")
            assertNode("FAILURE: Build completed with 2 failures.") {
              assertNode("build.gradle") {
                assertNode("Execution failed for task ':failingTask1'$failureSuffix")
                assertNode("Execution failed for task ':failingTask2'$failureSuffix")
              }
            }
          }
        }
        assertBuildViewNode(runView, "failed") {
          Assertions.assertThat(it.consoleText)
            .containsOnlyOnce("Task 1 doLast")
            .contains("Task 1 failure")
            .containsOnlyOnce("Task 2 doLast")
            .contains("Task 2 failure")
        }
        assertBuildViewNode(runView, ":failingTask1") {
          if (isPerTaskOutputSupported()) {
            Assertions.assertThat(it.consoleText)
              .containsOnlyOnce("Task 1 doLast")
              //.contains("Task 1 failure")
              .doesNotContain("Task 2 failure")
          }
        }
        assertBuildViewNode(runView, ":failingTask2") {
          if (isPerTaskOutputSupported()) {
            Assertions.assertThat(it.consoleText)
              .containsOnlyOnce("Task 2 doLast")
              //.contains("Task 2 failure")
              .doesNotContain("Task 1 failure")
          }
        }
      }
    }
}