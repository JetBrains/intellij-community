// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import com.intellij.openapi.util.RecursionManager
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleForeignDelegateInspection
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.junit.jupiter.params.ParameterizedTest

class GradleForeignDelegateInspectionTest  : GradleCodeInsightTestCase() {

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