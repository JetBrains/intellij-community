// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.junit.jupiter.api.Assumptions
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
  fun testAlreadyLatestMinorVersion(gradleVersion: GradleVersion) {
    assumeThatGradleVersionIsLatestMinor(gradleVersion)
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testDeprecatedVersion(gradleVersion: GradleVersion) {
    assumeThatGradleVersionIsDeprecated(gradleVersion)
    runTest(gradleVersion) {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testNotLatestMinorVersion(gradleVersion: GradleVersion) {
    assumeThatGradleVersionIsNotDeprecated(gradleVersion)
    assumeThatGradleVersionIsNotLatestMinor(gradleVersion)
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
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testUpgradeWhiteSpace(gradleVersion: GradleVersion) {
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testUpgradeCustomUrl(gradleVersion: GradleVersion) {
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun testUpgradeAllVersions(gradleVersion: GradleVersion) {
    assumeThatGradleVersionIsNotDeprecated(gradleVersion)
    assumeThatGradleVersionIsNotLatestMinor(gradleVersion)
    val latestGradleMinorVersion = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradleMinorVersion-bin.zip",
        "Upgrade to Gradle $latestGradleMinorVersion"
      )
    }
  }
}

private fun assumeThatGradleVersionIsLatestMinor(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(isLatestMinorGradleVersion(gradleVersion)) {
    "Gradle ${gradleVersion.version} is the latest minor version."
  }
}

private fun assumeThatGradleVersionIsNotLatestMinor(gradleVersion: GradleVersion) {
  Assumptions.assumeFalse(isLatestMinorGradleVersion(gradleVersion)) {
    "Gradle ${gradleVersion.version} is not the latest minor version."
  }
}

private fun assumeThatGradleVersionIsDeprecated(gradleVersion: GradleVersion) {
  Assumptions.assumeTrue(GradleJvmSupportMatrix.isGradleDeprecatedByIdea(gradleVersion)) {
    "Gradle ${gradleVersion.version} is deprecated by IntelliJ IDEA."
  }
}

private fun assumeThatGradleVersionIsNotDeprecated(gradleVersion: GradleVersion) {
  Assumptions.assumeFalse(GradleJvmSupportMatrix.isGradleDeprecatedByIdea(gradleVersion)) {
    "Gradle ${gradleVersion.version} is not deprecated by IntelliJ IDEA."
  }
}

private fun isLatestMinorGradleVersion(gradleVersion: GradleVersion): Boolean =
  gradleVersion >= GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion)