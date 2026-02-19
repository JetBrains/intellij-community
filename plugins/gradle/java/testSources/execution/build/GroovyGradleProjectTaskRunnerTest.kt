// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfigurationType
import org.junit.jupiter.params.ParameterizedTest

class GroovyGradleProjectTaskRunnerTest : GradleProjectTaskRunnerTestCase() {
  @ParameterizedTest(name = "[{index}] {0} delegatedBuild={1}, delegatedRun={2}")
  @BaseGradleVersionSource("""
    true:true:   true:true,
    true:false:  true:false,
    false:true:  false:false,
    false:false: false:false
  """)
  fun `test GradleProjectTaskRunner#canRun for GroovyScriptRunConfiguration`(
    gradleVersion: GradleVersion,
    delegatedBuild: Boolean, delegatedRun: Boolean,
    shouldBuild: Boolean, shouldRun: Boolean,
  ) {
    test(gradleVersion, GROOVY5_PROJECT) {
      Disposer.newDisposable().use { testDisposable ->
        val configurationType = GroovyScriptRunConfigurationType.getInstance()
        setupGradleDelegationMode(delegatedBuild, delegatedRun, testDisposable)
        assertGradleProjectTaskRunnerCanRun(configurationType, shouldBuild, shouldRun)
      }
    }
  }

  companion object {
    val GROOVY5_PROJECT = GradleTestFixtureBuilder.create("groovy-plugin-project", JavaVersionRestriction.javaRestrictionOf("11+")) { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("groovy-plugin-project")
      }
      withBuildFile(gradleVersion) {
        withGroovyPlugin("5.0.0")
      }
      withDirectory("src/main/java")
      withDirectory("src/main/groovy")
    }
  }
}