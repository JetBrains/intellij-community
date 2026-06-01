// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling

import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers
import org.hamcrest.CustomMatcher
import org.hamcrest.Matcher
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class VersionMatcherRule : TestWatcher() {

  private var myMatcher: CustomMatcher<String>? = null

  val matcher: Matcher<String>
    get() = myMatcher ?: CoreMatchers.any(String::class.java)

  override fun starting(d: Description) {
    val targetVersions = d.getAnnotation(TargetVersions::class.java)
    val onlyFirstLast = System.getProperty("gradle.versions.to.run") == "FIRST_LAST"
    val expectedDescription =
      "${if (onlyFirstLast) "Boundary " else ""}Gradle version ${targetVersions?.value?.contentToString()?.let { "'$it'" } ?: ""}"
    myMatcher = object : CustomMatcher<String>(expectedDescription) {
      override fun matches(item: Any?): Boolean {
        val gradleVersion = GradleVersion.version(item as String)
        if (!VersionMatcher(gradleVersion).isVersionMatch(targetVersions)) return false
        if (onlyFirstLast && !isBoundarySupportedGradleVersion(gradleVersion, targetVersions)) return false
        return true
      }
    }
  }

  /**
   * Returns true iff gradleVersion is a boundary version of [SUPPORTED_GRADLE_VERSIONS]
   * (after applying targetVersions filter on it if it's not null)
   */
  private fun isBoundarySupportedGradleVersion(gradleVersion: GradleVersion, targetVersions: TargetVersions?): Boolean {
    val allTargetedVersions = SUPPORTED_GRADLE_VERSIONS
      .filter { targetVersions == null || VersionMatcher(GradleVersion.version(it)).isVersionMatch(targetVersions) }

    return allTargetedVersions.firstOrNull() == gradleVersion.version || allTargetedVersions.lastOrNull() == gradleVersion.version
  }

  companion object {
    /**
     * Note: When adding new versions here, change also lists:
     * - Idea_Tests_BuildToolsTests
     * - IntelliJ TeamCity configuration
     * - [VersionMatcherRule.BASE_GRADLE_VERSION]
     * - [org.jetbrains.plugins.gradle.jvmcompat.DEFAULT_DATA]
     * - "gradle.versions.list" file in the resources
     */
    val SUPPORTED_GRADLE_VERSIONS = listOf(
      "4.6", /*"4.7", "4.8.1", "4.9",*/ "4.10.3",
      "5.0", /*"5.1.1", "5.2.1", "5.3.1", "5.4.1", "5.5.1",*/ "5.6.4",
      "6.0.1", /*"6.1.1", "6.2.2", "6.3", "6.4.1", "6.5.1", "6.6.1", "6.7.1", "6.8.3",*/ "6.9.4",
      "7.0.2", /*"7.1.1", "7.2", "7.3.3", "7.4.2", "7.5.1",*/ "7.6.6",
      "8.0.2", /*"8.1.1", "8.2.1", "8.3", "8.4", "8.5", "8.6", "8.7", "8.8", "8.9", "8.10.2", "8.11.1", "8.12.1", "8.13",*/ "8.14.5",
      "9.0.0", /*"9.1.0", "9.2.1", "9.3.1", "9.4.1", "9.5.1",*/ "9.6.0-rc-1"
    )

    const val BASE_GRADLE_VERSION = "9.6.0-rc-1"

    @JvmStatic
    fun getSupportedGradleVersions(): List<String> {
      val gradleVersionsString = System.getProperty("gradle.versions.to.run")
      return if (!gradleVersionsString.isNullOrEmpty()) {
        if (gradleVersionsString.startsWith("LAST:")) {
          val last = gradleVersionsString.removePrefix("LAST:").toInt()
          SUPPORTED_GRADLE_VERSIONS.takeLast(last)
        }
        else if (gradleVersionsString == "FIRST_LAST") {
          SUPPORTED_GRADLE_VERSIONS // filtering done later
        }
        else {
          gradleVersionsString.split(",")
        }
      }
      else SUPPORTED_GRADLE_VERSIONS
    }
  }
}