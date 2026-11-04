// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.junit4

import com.intellij.gradle.testFramework.GradleTestVersions.SUPPORTED_GRADLE_VERSIONS
import com.intellij.gradle.testFramework.annotations.TargetVersions
import com.intellij.gradle.testFramework.util.VersionMatcher
import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers
import org.hamcrest.CustomMatcher
import org.hamcrest.Matcher
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