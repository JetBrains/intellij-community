// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleBuildScriptBuilderUtil")

package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion
import org.gradle.util.GradleVersion.version

/**
 * Embedded Kotlin version     Minimum Gradle version      Kotlin Language version
 *
 * 1.3.10                      5.0                         1.3
 * 1.3.11                      5.1                         1.3
 * 1.3.20                      5.2                         1.3
 * 1.3.21                      5.3                         1.3
 * 1.3.31                      5.5                         1.3
 * 1.3.41                      5.6                         1.3
 * 1.3.50                      6.0                         1.3
 * 1.3.61                      6.1                         1.3
 * 1.3.70                      6.3                         1.3
 * 1.3.71                      6.4                         1.3
 * 1.3.72                      6.5                         1.3
 * 1.4.20                      6.8                         1.3
 * 1.4.31                      7.0                         1.4
 * 1.5.21                      7.2                         1.4
 * 1.5.31                      7.3                         1.4
 * 1.6.21                      7.5                         1.4
 * 1.7.10                      7.6                         1.4
 * 1.8.10                      8.0                         1.8
 * 1.8.20                      8.2                         1.8
 * 1.9.0                       8.3                         1.8
 * 1.9.10                      8.4                         1.8
 * 1.9.20                      8.5                         1.8
 * 1.9.22                      8.7                         1.8
 * 1.9.23                      8.9                         1.8
 * 1.9.24                      8.10                        1.8
 * 2.0.20                      8.11                        1.8
 * 2.0.21                      8.12                        1.8
 *
 * Source: https://docs.gradle.org/current/userguide/compatibility.html
 */
fun getKotlinVersion(gradleVersion: GradleVersion): String {
  val base = gradleVersion.baseVersion
  return when {
    base >= version("8.12") -> "2.0.21"
    base >= version("8.11") -> "2.0.20"
    base >= version("8.10") -> "1.9.24"
    base >= version("8.7") -> "1.9.23"
    base >= version("6.7.1") -> "1.7.20"
    base >= version("5.6.2") -> "1.4.32"
    else -> "1.3.50"
  }
}

fun getGroovyVersion(): String {
  return "3.0.5"
}

fun getJunit4Version(): String {
  return "4.13.2"
}

fun getJunit5Version(): String {
  return "5.10.0"
}

fun isTaskConfigurationAvoidanceSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "4.9")
}

fun isKotlinSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "5.6.2")
}

fun isJunit5Supported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "4.7")
}

fun isSpockSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "5.6")
}

fun isRobolectricSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "4.6")
}

fun isTopLevelJavaConventionsSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.2")
}

fun isJavaConventionsBlockSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.1")
}

fun isConfigurationCacheSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "8.1")
}

fun isIsolatedProjectsSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "8.8")
}

fun isPlatformDependencySupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "5.0")
}

/**
 * Gradle: Deprecate loading test framework implementation dependencies from the distribution #24189
 * [https://github.com/gradle/gradle/pull/24189]
 */
fun isExplicitTestFrameworkRuntimeDeclarationRequired(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "8.2")
}

fun isGroovyApacheSupported(groovyVersion: String): Boolean {
  val majorVersion = groovyVersion.split(".").firstOrNull()?.let(Integer::valueOf) ?: 0
  return majorVersion >= 4
}
