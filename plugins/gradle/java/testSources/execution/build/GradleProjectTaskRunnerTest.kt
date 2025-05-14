// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.scratch.JavaScratchConfigurationType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleProjectTaskRunnerTest : GradleProjectTaskRunnerTestCase() {

  @ParameterizedTest(name = "[{index}] {0} delegatedBuild={1}, delegatedRun={2}")
  @BaseGradleVersionSource("""
    true:true:   true:true,
    true:false:  true:false,
    false:true:  false:false,
    false:false: false:false
  """)
  fun `test GradleProjectTaskRunner#canRun for ApplicationConfiguration`(
    gradleVersion: GradleVersion,
    delegatedBuild: Boolean, delegatedRun: Boolean,
    shouldBuild: Boolean, shouldRun: Boolean,
  ) {
    testEmptyProject(gradleVersion) {
      Disposer.newDisposable().use { testDisposable ->
        val configurationType = ApplicationConfigurationType.getInstance()
        setupGradleDelegationMode(delegatedBuild, delegatedRun, testDisposable)
        assertGradleProjectTaskRunnerCanRun(configurationType, shouldBuild, shouldRun)
      }
    }
  }

  @ParameterizedTest(name = "[{index}] {0} delegatedBuild={1}, delegatedRun={2}")
  @BaseGradleVersionSource("true,false", "true,false")
  fun `test GradleProjectTaskRunner#canRun for JavaScratchConfiguration`(
    gradleVersion: GradleVersion,
    delegatedBuild: Boolean, delegatedRun: Boolean,
  ) {
    testEmptyProject(gradleVersion) {
      Disposer.newDisposable().use { testDisposable ->
        val configurationType = JavaScratchConfigurationType.getInstance()
        setupGradleDelegationMode(delegatedBuild, delegatedRun, testDisposable)
        assertGradleProjectTaskRunnerCanRun(configurationType, shouldBuild = false, shouldRun = false)
      }
    }
  }
}