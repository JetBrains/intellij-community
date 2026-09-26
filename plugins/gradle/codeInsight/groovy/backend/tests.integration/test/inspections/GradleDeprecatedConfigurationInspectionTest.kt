// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.groovy.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleDeprecatedConfigurationInspection
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.params.ParameterizedTest

class GradleDeprecatedConfigurationInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleDeprecatedConfigurationInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @GradleTestSource("7.4.2")
  fun testDetectDeprecatedElement(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("dependencies { <warning>apiElements</warning>('abc') }")
    }
  }

  @ParameterizedTest
  @GradleTestSource("7.4.2")
  fun testCreateToRegister(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testIntention("dependencies { ap<caret>iElements('abc') }", "dependencies { compileOnly('abc') }",
                    "Replace 'apiElements' with 'compileOnly'")
    }
  }
}