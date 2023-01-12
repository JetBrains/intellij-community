// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleJvmSupportMatrices")
package org.jetbrains.plugins.gradle.util

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion

// Java versions which can be suggested to use with Intellij Idea
private val SUPPORTED_JAVA_VERSIONS = listOf(
  7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19
).map(JavaVersion::compose)

val MINIMUM_SUPPORTED_JAVA = SUPPORTED_JAVA_VERSIONS.first()
val MAXIMUM_SUPPORTED_JAVA = SUPPORTED_JAVA_VERSIONS.last()

// Gradle versions which can be suggested to use with Intellij Idea
private val SUPPORTED_GRADLE_VERSIONS = listOf(
  "3.0", "3.1", "3.2", "3.3", "3.4", "3.5",
  "4.0", "4.1", "4.2", "4.3", "4.4", "4.5", "4.5.1", "4.6", "4.7", "4.8", "4.9", "4.10", "4.10.3",
  "5.0", "5.1", "5.2", "5.3", "5.3.1", "5.4", "5.4.1", "5.5", "5.5.1", "5.6", "5.6.2",
  "6.0", "6.0.1", "6.1", "6.2", "6.3", "6.4", "6.5", "6.6", "6.7", "6.8", "6.8.3", "6.9",
  "7.0", "7.1", "7.2", "7.3", "7.4", "7.5", "7.5.1"
).map(GradleVersion::version)

// Sync with https://docs.gradle.org/current/userguide/compatibility.html
private val COMPATIBILITY = listOf(
  // https://docs.gradle.org/5.0/release-notes.html#potential-breaking-changes
  range(6 to 8) to range(INF to "5.0"),
  // Gradle older than 2.0 unofficially compatible with Java 8
  // Gradle from 5.1 to 7.1 and Java 8 aren't compatible
  // https://github.com/gradle/gradle/issues/8285
  range(8 to 9) to range(INF to "5.1", "7.2" to INF),
  range(9 to 10) to range("4.3" to INF),
  range(10 to 11) to range("4.7" to INF),
  range(11 to 12) to range("5.0" to INF),
  range(12 to 13) to range("5.4" to INF),
  range(13 to 14) to range("6.0" to INF),
  range(14 to 15) to range("6.3" to INF),
  // Many builds might work with Java 15 but there are some known issues
  // https://github.com/gradle/gradle/issues/13532
  range(15 to 16) to range("6.7" to INF),
  range(16 to 17) to range("7.0" to INF),
  // Gradle 7.2 and Java 17 are partially compatible
  // https://github.com/gradle/gradle/issues/16857
  range(17 to 18) to range("7.2" to INF),
  range(18 to 19) to range("7.5" to INF),
  range(19 to INF) to range("7.6" to INF)
).map {
  it.first.map(JavaVersion::compose) to
    it.second.map(GradleVersion::version)
}

fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
  return COMPATIBILITY.any { (javaVersions, gradleVersions) ->
    javaVersion in javaVersions && gradleVersion in gradleVersions
  }
}

fun suggestGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  val gradleVersion = GradleVersion.current()
  if (isSupported(gradleVersion, javaVersion)) {
    return gradleVersion
  }
  return SUPPORTED_GRADLE_VERSIONS.reversed().find { isSupported(it, javaVersion) }
}

fun suggestJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return SUPPORTED_JAVA_VERSIONS.reversed().find { isSupported(gradleVersion, it) }
}

fun suggestOldestCompatibleGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return SUPPORTED_GRADLE_VERSIONS.find { isSupported(it, javaVersion) }
}

fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return SUPPORTED_JAVA_VERSIONS.find { isSupported(gradleVersion, it) }
}
