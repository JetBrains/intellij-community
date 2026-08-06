// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewNode
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.consoleText
import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest

class GradleExecutionOutputTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task execution output without failures`(gradleVersion: GradleVersion) {
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
          .contains("Task doLast")
      }
      assertBuildViewNode(runView, ":task") {
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(it.consoleText)
            .contains("Task doLast")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task execution output with one failure`(gradleVersion: GradleVersion) {
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
      assertBuildViewTree(runView) {
        assertNode("failed") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNode(":failingTask") {
            assertNode("build.gradle") {
              assertNode("java.lang.Exception: Task failure")
            }
          }
        }
      }
      assertBuildViewNode(runView, "failed") {
        Assertions.assertThat(it.consoleText)
          .contains("Task doLast")
          .contains("Task failure")
      }
      assertBuildViewNode(runView, ":failingTask") {
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(it.consoleText)
            .contains("Task doLast")
          //.contains("Task failure")
        }
      }
      assertBuildViewNode(runView, "java.lang.Exception: Task failure") {
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(it.consoleText)
            .contains("Task failure")
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test task execution output with two failures`(gradleVersion: GradleVersion) {
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
      assertBuildViewTree(runView) {
        assertNode("failed") {
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNode(":failingTask1")
          assertNode(":failingTask2")
        }
      }
      assertBuildViewNode(runView, "failed") {
        Assertions.assertThat(it.consoleText)
          .contains("Task 1 doLast")
          .contains("Task 1 failure")
          .contains("Task 2 doLast")
          .contains("Task 2 failure")
      }
      assertBuildViewNode(runView, ":failingTask1") {
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(it.consoleText)
            .contains("Task 1 doLast")
            //.contains("Task 1 failure")
            .doesNotContain("Task 2 failure")
        }
      }
      assertBuildViewNode(runView, ":failingTask2") {
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(it.consoleText)
            .contains("Task 2 doLast")
            //.contains("Task 2 failure")
            .doesNotContain("Task 1 failure")
        }
      }
    }
  }
}