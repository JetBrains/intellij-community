// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleBuildScriptBuilderUtil")

package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion
import org.gradle.util.GradleVersion.version


fun getKotlinVersion(gradleVersion: GradleVersion): String {
  val base = gradleVersion.baseVersion
  return when {
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

fun isJunit5Supported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "4.7")
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
