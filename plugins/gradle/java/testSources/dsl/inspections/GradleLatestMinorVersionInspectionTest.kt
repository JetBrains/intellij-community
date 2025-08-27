// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleLatestMinorVersionInspection
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.BASE_GRADLE_VERSION
import org.jetbrains.plugins.gradle.util.GradleUtil.getWrapperDistributionUri
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
    val wrapperDistribution = getWrapperDistributionUri(gradleVersion).toString()

    // no warning markers
    runTest {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=$wrapperDistribution"
      )
    }
  }

  @ParameterizedTest
  @ArgumentsSource(NonLatestMinorGradleVersionsProvider::class)
  fun testNotLatestMinorVersion(gradleVersion: GradleVersion) {
    // put warning markers around the version text
    val wrapperDistributionWithWarning = getWrapperDistributionUri(gradleVersion).toString()
      .replace(gradleVersion.version, "<warning>${gradleVersion.version}</warning>")
    runTest {
      testHighlighting(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=$wrapperDistributionWithWarning"
      )
    }
  }

  @Test
  fun testUpgrade() {
    val latestGradle8Version = GradleJvmSupportMatrix.getLatestMinorGradleVersion(8).version

    runTest {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-<caret>8.13-bin.zip",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-$latestGradle8Version-bin.zip",
        "Upgrade to Gradle $latestGradle8Version"
      )
    }
  }

  @ParameterizedTest
  @ArgumentsSource(NonLatestMinorGradleVersionsProvider::class)
  fun testUpgradeAllVersions(gradleVersion: GradleVersion) {
    val latestGradleMinorVersion = GradleJvmSupportMatrix.getLatestMinorGradleVersion(gradleVersion.majorVersion)

    val wrapperDistribution = getWrapperDistributionUri(gradleVersion).toString().replace(":", "\\:")
      .replace(gradleVersion.version, "<caret>${gradleVersion.version}")
    val upgradedWrapperDistribution = getWrapperDistributionUri(latestGradleMinorVersion)
      .toString().replace(":", "\\:")

    runTest {
      testIntention(
        "gradle/wrapper/gradle-wrapper.properties",
        "distributionUrl=$wrapperDistribution",
        "distributionUrl=$upgradedWrapperDistribution",
        "Upgrade to Gradle ${latestGradleMinorVersion.version}"
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