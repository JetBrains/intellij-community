// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleCodeInsightTestFixture
import org.junit.jupiter.params.ParameterizedTest

class GradlePublishingTest : GradleCodeInsightTestCase() {

  override fun createGradleTestFixture(gradleVersion: GradleVersion): GradleCodeInsightTestFixture =
    createGradleCodeInsightTestFixture(gradleVersion, "maven-publish")

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test publishing closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testBuildscript(gradleVersion, decorator, "publishing { <caret> }") {
      closureDelegateTest(getPublishingExtensionFqn(), 1)
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test publishing repositories maven url`(gradleVersion: GradleVersion, decorator: String) {
    testBuildscript(gradleVersion, decorator, "publishing { repositories { maven { url<caret> '' } } }") {
      setterMethodTest("url", "setUrl", GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY)
    }
  }
}