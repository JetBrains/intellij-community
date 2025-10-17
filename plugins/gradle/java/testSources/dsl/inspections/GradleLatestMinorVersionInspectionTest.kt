// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.BASE_GRADLE_VERSION
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

class GradleLatestMinorVersionInspectionTest : GradleCodeInsightTestCase() {

  private fun runTest(test: () -> Unit) {
    testEmptyProject(GradleVersion.version(BASE_GRADLE_VERSION)) {
      codeInsightFixture.enableInspections(GradleLatestMinorVersionInspection::class.java)
      test()
    }
  }

  @ParameterizedTest
  @ArgumentsSource(LatestMinorGradleVersionsProvider::class)
  fun testAlreadyLatestMinorVersion(gradleVersion: GradleVersion) {
    runTest {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip"
      )
    }
  }

  @ParameterizedTest
  @ArgumentsSource(NonLatestMinorGradleVersionsProvider::class)
  fun testNotLatestMinorVersion(gradleVersion: GradleVersion) {
    runTest {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-<warning>${gradleVersion.version}</warning>-bin.zip"
      )
    }
  }

  @Test
  fun testWhiteSpaceInProperty() {
    runTest {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-<warning>8.13</warning>-bin.zip"
      )
    }
  }

  @Test
  fun testCustomUrl() {
    runTest {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-<warning>8.13</warning>-bin.zip"
      )
    }
  }

  @Test
  fun testUpgrade() {
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(8).version

    runTest {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @Test
  fun testUpgradeWhiteSpace() {
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(8).version

    runTest {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-8.13<caret>-bin.zip",
        "distributionUrl = https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @Test
  fun testUpgradeCustomUrl() {
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(8).version

    runTest {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-8.13<caret>-bin.zip",
        "distributionUrl=https\\://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @ArgumentsSource(NonLatestMinorGradleVersionsProvider::class)
  fun testUpgradeAllVersions(gradleVersion: GradleVersion) {
    val latestGradleMinorVersion = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion).version

    runTest {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion.version}<caret>-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradleMinorVersion-bin.zip",
        "Upgrade to Gradle $latestGradleMinorVersion"
      )
    }
  }
}

private class LatestMinorGradleVersionsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
    val versions = VersionMatcherRule.getSupportedGradleVersions().map {
      GradleVersion.version(it)
    }.filter {
      it >= GradleJvmSupportMatrix.getLatestMinorGradleVersion(it.majorVersion)
    }
    return versions.map { Arguments.of(it) }.stream()
  }
}

private class NonLatestMinorGradleVersionsProvider : ArgumentsProvider {
  override fun provideArguments(parameters: ParameterDeclarations, context: ExtensionContext): Stream<out Arguments> {
    val versions = VersionMatcherRule.getSupportedGradleVersions().map {
      GradleVersion.version(it)
    }.filter {
      it < GradleJvmSupportMatrix.getLatestMinorGradleVersion(it.majorVersion)
    }
    return versions.map { Arguments.of(it) }.stream()
  }
}