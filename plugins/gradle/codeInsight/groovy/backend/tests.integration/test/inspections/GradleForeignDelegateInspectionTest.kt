// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.groovy.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleForeignDelegateInspection
import com.intellij.openapi.util.RecursionManager
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleForeignDelegateInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    testJavaProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleForeignDelegateInspection::class.java)
      test()
    }
  }


  @ParameterizedTest
  @BaseGradleVersionSource
  fun testIncorrectMethodInRepositories(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("repositories { maven { <weak_warning>google</weak_warning>() }}")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoWarningForProject(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("dependencies { implementation(files('')) }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoWarningForProvider(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("tasks.configure { named('30').configure { named('40') } }")
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testNoWarningForTask(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      RecursionManager.disableMissedCacheAssertions(fixture.testRootDisposable)
      testHighlighting("tasks.register('Hello', Delete) { doFirst { delete() } }")
    }
  }
}