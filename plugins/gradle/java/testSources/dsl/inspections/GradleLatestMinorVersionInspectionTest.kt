// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.util.DEPRECATED_BY_IDEA_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.NON_DEPRECATED_BY_IDEA_VERSIONS
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.Companion.BASE_GRADLE_VERSION
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleLatestMinorVersionInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleLatestMinorVersionInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions("6.1+", reason = "Test non-deprecated by Idea Gradle versions and skip 6.0")
  fun testAlreadyLatestMinorVersion(gradleVersion: GradleVersion) {
    assumeTrue(isLatestMinorGradleVersion(gradleVersion)) {
      "Gradle ${gradleVersion.version} is not the latest minor version."
    }
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions(DEPRECATED_BY_IDEA_VERSIONS, reason = "Only test deprecated by Idea Gradle versions")
  fun testDeprecatedVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions(
    NON_DEPRECATED_BY_IDEA_VERSIONS, "<$BASE_GRADLE_VERSION",
    reason = "Test non-latest minor Gradle versions, skip current latest"
  )
  fun testNotLatestMinorVersion(gradleVersion: GradleVersion) {
    assumeFalse(isLatestMinorGradleVersion(gradleVersion)) {
      "Gradle ${gradleVersion.version} is the latest minor version."
    }
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testWhiteSpaceInProperty(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testCustomUrl(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testUpgrade(gradleVersion: GradleVersion) {
    val latestGradle8Version = GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Switch to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testUpgradeWhiteSpace(gradleVersion: GradleVersion) {
    val latestGradle8Version = GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Switch to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testUpgradeCustomUrl(gradleVersion: GradleVersion) {
    val latestGradle8Version = GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Switch to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions(
    NON_DEPRECATED_BY_IDEA_VERSIONS, "<$BASE_GRADLE_VERSION",
    reason = "Test non-latest minor Gradle versions, skip current latest"
  )
  fun testUpgradeAllVersions(gradleVersion: GradleVersion) {
    assumeFalse(isLatestMinorGradleVersion(gradleVersion)) {
      "Gradle ${gradleVersion.version} is the latest minor version."
    }

    val latestGradleMinorVersion = GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradleMinorVersion-bin.zip",
        "Switch to Gradle $latestGradleMinorVersion"
      )
    }
  }
}

private fun isLatestMinorGradleVersion(gradleVersion: GradleVersion): Boolean =
  gradleVersion >= GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion)