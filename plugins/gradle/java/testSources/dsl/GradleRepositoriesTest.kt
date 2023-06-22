// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest

class GradleRepositoriesTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test repositories closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { <caret> }") {
        closureDelegateTest(GRADLE_API_REPOSITORY_HANDLER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test maven repository closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { maven { <caret> } }") {
        closureDelegateTest(GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test ivy repository closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { ivy { <caret> } }") {
        closureDelegateTest(GRADLE_API_ARTIFACTS_REPOSITORIES_IVY_ARTIFACT_REPOSITORY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test flat repository closure delegate`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { flatDir { <caret> } }") {
        closureDelegateTest(GRADLE_API_ARTIFACTS_REPOSITORIES_FLAT_DIRECTORY_ARTIFACT_REPOSITORY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test maven repository method setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { maven { <caret>url(42) } }") {
        setterMethodTest("url", "setUrl", GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test ivy repository method setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { ivy { <caret>url('') } }") {
        setterMethodTest("url", "setUrl", GRADLE_API_ARTIFACTS_REPOSITORIES_IVY_ARTIFACT_REPOSITORY)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$DECORATORS, buildscript")
  fun `test flat repository method setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "repositories { flatDir { <caret>name('') } }") {
        setterMethodTest("name", "setName", GRADLE_API_ARTIFACTS_REPOSITORIES_ARTIFACT_REPOSITORY)
      }
    }
  }
}