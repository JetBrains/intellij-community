// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.params.ParameterizedTest

class GradlePublishingTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test publishing closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "publishing { <caret> }") {
        closureDelegateTest(getPublishingExtensionFqn(), 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test publishing repositories maven url`(gradleVersion: GradleVersion, decorator: String) {
    test(gradleVersion, FIXTURE_BUILDER) {
      testBuildscript(decorator, "publishing { repositories { maven { url<caret> '' } } }") {
        setterMethodTest("url", "setUrl", GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY)
      }
    }
  }

  companion object {

    private val FIXTURE_BUILDER = GradleTestFixtureBuilder.create("GradlePublishingTest") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradlePublishingTest")
      }
      withBuildFile(gradleVersion) {
        withPlugin("maven-publish")
      }
    }
  }
}