// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.execution.scratch.JavaScratchConfigurationType
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleProjectTaskRunnerTest : GradleProjectTaskRunnerTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GradleProjectTaskRunner#canRun with application configuration`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      `test GradleProjectTaskRunner#canRun`(
        ApplicationConfigurationType.getInstance(),
        shouldRunWithModule = true,
        shouldRunWithoutModule = false,
        shouldBuildWithModule = true,
        shouldBuildWithoutModule = false
      )
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test GradleProjectTaskRunner#canRun with Java scratch configuration`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      `test GradleProjectTaskRunner#canRun`(
        JavaScratchConfigurationType.getInstance(),
        shouldRunWithModule = false,
        shouldRunWithoutModule = false,
        shouldBuildWithModule = false,
        shouldBuildWithoutModule = false
      )
    }
  }
}