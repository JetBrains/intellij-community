// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleBuildscriptStructureInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleBuildscriptStructureTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test disallowed statement`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleBuildscriptStructureInspection::class.java)
      testHighlighting("""
        <error>apply</error> plugin: 'java'
        plugins {}
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test allowed statement`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleBuildscriptStructureInspection::class.java)
      testHighlighting("""
        buildscript {}
        plugins {}
        """.trimIndent())
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test import`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleBuildscriptStructureInspection::class.java)
      testHighlighting("""
        <warning>import java.math.BigInteger</warning>
        plugins {}
        """.trimIndent())
    }
  }
}