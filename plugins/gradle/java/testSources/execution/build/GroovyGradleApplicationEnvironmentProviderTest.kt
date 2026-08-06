// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.RunManager
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewNode
import com.intellij.platform.testFramework.assertion.BuildViewAssertions.assertBuildViewTree
import com.intellij.platform.testFramework.assertion.consoleText
import com.intellij.util.LocalTimeCounter
import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.execution.build.GroovyGradleProjectTaskRunnerTest.Companion.GROOVY5_PROJECT
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.GROOVY_5_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfigurationType
import org.junit.jupiter.params.ParameterizedTest

class GroovyGradleApplicationEnvironmentProviderTest : GradleExecutionTestCase() {
  @AllGradleVersionsSource
  @ParameterizedTest
  @TargetVersions(GROOVY_5_SUPPORTED_VERSIONS)
  fun `test run groovy 5 via gradle`(
    gradleVersion: GradleVersion,
  ) {
    test(gradleVersion, GROOVY5_PROJECT) {

      writeText("src/main/groovy/org/example/App.groovy", """
        package org.example
          void main() {
            println 'Hello from Groovy!'
          }
      """.trimIndent())
      val runManager = RunManager.getInstance(project)
      val runConfigurationName = "GradleExecutionTestFixture(" + LocalTimeCounter.currentTime() + ")"
      val runnerSettings = runManager.createConfiguration(runConfigurationName, GroovyScriptRunConfigurationType::class.java)
      val runConfiguration = runnerSettings.configuration as GroovyScriptRunConfiguration
      runConfiguration.module = mainModule
      runConfiguration.scriptPath = "$projectPath/src/main/groovy/org/example/App.groovy"
      val environment = executionFixture.createExecutionEnvironment(
        runnerSettings,
        false
      )

      executionFixture.execute(environment)

      assertBuildViewTree(runView) {
        assertNode("successful") {
          assertNode(":compileJava")
          assertNode(":compileGroovy")
          assertNode(":processResources")
          assertNode(":classes")
          assertNode(":$runConfigurationName.main()")
        }
      }
      assertBuildViewNode(runView, "successful") {
        Assertions.assertThat(it.consoleText)
          .contains("Hello from Groovy!")
      }
    }
  }
}