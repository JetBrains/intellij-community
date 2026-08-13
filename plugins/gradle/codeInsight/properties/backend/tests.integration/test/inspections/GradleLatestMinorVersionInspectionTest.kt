// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.codeInsight.properties.backend.tests.integration.inspections

import com.intellij.gradle.codeInsight.backend.inspections.declarations.GradleLatestMinorVersionInspection
import com.intellij.testFramework.runInEdtAndWait
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightBaseTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.annotations.GradleTestSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.testFramework.util.DEPRECATED_BY_IDEA_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.NON_DEPRECATED_BY_IDEA_VERSIONS
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.Companion.BASE_GRADLE_VERSION
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest

@GradleProjectTestApplication
class GradleLatestMinorVersionInspectionTest : GradleCodeInsightBaseTestCase() {

  private fun runTest(gradleVersion: GradleVersion, test: () -> Unit) {
    testEmptyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GradleLatestMinorVersionInspection::class.java)
      test()
    }
  }

  private fun testHighlighting(expression: String) {
    val relativePath = "gradle/wrapper/gradle-wrapper.properties"
    writeTextAndCommit(relativePath, expression)
    runInEdtAndWait {
      codeInsightFixture.testHighlighting(true, false, true, getFile(relativePath))
    }
  }

  private fun testIntention(before: String, after: String, intentionPrefix: String) {
    assertTrue("<caret>" in before, "Please define caret position in build script.")
    val relativePath = "gradle/wrapper/gradle-wrapper.properties"
    writeTextAndCommit(relativePath, before)
    runInEdtAndWait {
      codeInsightFixture.configureFromExistingVirtualFile(getFile(relativePath))
      val intention = codeInsightFixture.filterAvailableIntentions(intentionPrefix).single()
      codeInsightFixture.launchAction(intention)
      codeInsightFixture.checkResult(after)
      gradleFixture.fileFixture.rollback(relativePath)
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
      testHighlighting("distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  @TargetVersions(DEPRECATED_BY_IDEA_VERSIONS, reason = "Only test deprecated by Idea Gradle versions")
  fun testDeprecatedVersion(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting("distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("bin,all")
  @TargetVersions(
    NON_DEPRECATED_BY_IDEA_VERSIONS, "<$BASE_GRADLE_VERSION",
    reason = "Test non-latest minor Gradle versions, skip current latest"
  )
  fun testNotLatestMinorVersion(gradleVersion: GradleVersion, distributionType: String) {
    assumeFalse(isLatestMinorGradleVersion(gradleVersion)) {
      "Gradle ${gradleVersion.version} is the latest minor version."
    }
    runTest(gradleVersion) {
      testHighlighting(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-$distributionType.zip"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testWhiteSpaceInProperty(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13")
  fun testCustomUrl(gradleVersion: GradleVersion) {
    runTest(gradleVersion) {
      testHighlighting(
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13", "bin,all")
  fun testUpgrade(gradleVersion: GradleVersion, distributionType: String) {
    val latestGradle8Version = GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-$distributionType.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-$distributionType.zip",
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
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Switch to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @GradleTestSource("8.13", "bin,all")
  fun testUpgradeCustomUrl(gradleVersion: GradleVersion, distributionType: String) {
    val latestGradle8Version = GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest(gradleVersion) {
      testIntention(
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-$distributionType.zip",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$latestGradle8Version-$distributionType.zip",
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
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradleMinorVersion-bin.zip",
        "Switch to Gradle $latestGradleMinorVersion"
      )
    }
  }
}

private fun isLatestMinorGradleVersion(gradleVersion: GradleVersion): Boolean =
  gradleVersion >= GradleJvmSupportMatrix.suggestLatestMinorGradleVersion(gradleVersion.majorVersion)