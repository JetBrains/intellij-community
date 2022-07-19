// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleEagernessInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.params.ParameterizedTest

class GradleEagernessInspectionTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testCreateToRegister(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.<warning>create</warning>('abc')")
    }
  }

  @ParameterizedTest
  @GradleTestSource("4.8")
  fun testNoWarningWithOldGradle(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleEagernessInspection::class.java)
      testHighlighting("tasks.create('abc')")
    }
  }
}