// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.inspections

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.enableInspectionTool
import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GradleLatestMinorVersionBuildIssueTaskExecutionTest : GradleExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testTaskExecution(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      enableGradleLatestMinorVersionInspection(project)

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
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNodeWithNewMinorGradleVersionInfo(gradleVersion)
          assertNode(":task")
        }
      }
      if (gradleVersion < GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion)) {
        assertRunViewConsoleText("New Minor Gradle Version Available") { consoleText ->
          assertNewMinorGradleVersionNodeConsoleText(gradleVersion, consoleText)
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["8.0"])
  fun testTaskExecutionDisabledInspection(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
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
          assertNodeWithDeprecatedGradleWarning(gradleVersion)
          assertNode(":task")
        }
      }
    }
  }

  companion object {
    internal fun enableGradleLatestMinorVersionInspection(project: Project) {
      val tool = InspectionTestUtil.instantiateTool(GradleLatestMinorVersionInspection::class.java)
      enableInspectionTool(project, tool, (project as ProjectEx).getEarlyDisposable())
    }

    internal fun SimpleTreeAssertion.Node<Nothing?>.assertNodeWithNewMinorGradleVersionInfo(gradleVersion: GradleVersion) {
      if (gradleVersion < GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion)) {
        assertNode("New Minor Gradle Version Available")
      }
    }

    internal fun assertNewMinorGradleVersionNodeConsoleText(gradleVersion: GradleVersion, consoleText: String) {
      val oldVersion = gradleVersion.version
      val newVersion = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion).version
      Assertions.assertThat(consoleText)
        .isEqualToIgnoringNewLines("""
          Gradle version $oldVersion is currently being used. We recommend upgrading to Gradle version $newVersion.
      
          Possible solutions:
           - Upgrade to Gradle $newVersion and re-sync
           - Edit inspection settings
          """.trimIndent()
        )
    }
  }
}

