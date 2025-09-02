// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
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
      assertRunViewTree {
        assertNode("successful") {
          assertNode(":task")
        }
      }
      assertRunViewConsoleText("successful") { consoleText ->
        Assertions.assertThat(consoleText)
          .contains("Task doLast")
      }
      assertRunViewConsoleText(":task") { consoleText ->
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(consoleText)
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
      assertRunViewTree {
        assertNode("failed") {
          assertNode(":failingTask") {
            assertNode("build.gradle") {
              assertNode("java.lang.Exception: Task failure")
            }
          }
        }
      }
      assertRunViewConsoleText("failed") { consoleText ->
        Assertions.assertThat(consoleText)
          .contains("Task doLast")
          .contains("Task failure")
      }
      assertRunViewConsoleText(":failingTask") { consoleText ->
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(consoleText)
            .contains("Task doLast")
          //.contains("Task failure")
        }
      }
      assertRunViewConsoleText("java.lang.Exception: Task failure") { consoleText ->
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(consoleText)
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
      assertRunViewTree {
        assertNode("failed") {
          assertNode(":failingTask1")
          assertNode(":failingTask2")
        }
      }
      assertRunViewConsoleText("failed") { consoleText ->
        Assertions.assertThat(consoleText)
          .contains("Task 1 doLast")
          .contains("Task 1 failure")
          .contains("Task 2 doLast")
          .contains("Task 2 failure")
      }
      assertRunViewConsoleText(":failingTask1") { consoleText ->
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(consoleText)
            .contains("Task 1 doLast")
            //.contains("Task 1 failure")
            .doesNotContain("Task 2 failure")
        }
      }
      assertRunViewConsoleText(":failingTask2") { consoleText ->
        if (isPerTaskOutputSupported()) {
          Assertions.assertThat(consoleText)
            .contains("Task 2 doLast")
            //.contains("Task 2 failure")
            .doesNotContain("Task 1 failure")
        }
      }
    }
  }
}