// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleBuildScriptBuilderUtil")

package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion

fun GradleVersion.isGradleAtLeast(version: String): Boolean =
  baseVersion >= GradleVersion.version(version)

fun GradleVersion.isGradleOlderThan(version: String): Boolean =
  baseVersion < GradleVersion.version(version)

fun getKotlinVersion(gradleVersion: GradleVersion): String {
  return when {
    gradleVersion.isGradleAtLeast("6.7.1") -> "1.7.20"
    gradleVersion.isGradleAtLeast("5.6.2") -> "1.4.32"
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
  return "5.9.1"
}

fun isJavaLibraryPluginSupported(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.isGradleAtLeast("3.4")
}

fun isImplementationScopeSupported(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.isGradleAtLeast("3.4")
}

fun isRuntimeOnlyScopeSupported(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.isGradleAtLeast("3.4")
}

fun isTaskConfigurationAvoidanceSupported(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.isGradleAtLeast("4.9")
}

fun isJunit5Supported(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.isGradleAtLeast("4.7")
}

fun isPlatformDependencySupported(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.isGradleAtLeast("5.0")
}

fun isGroovyApacheSupported(groovyVersion: String): Boolean {
  val majorVersion = groovyVersion.split(".").firstOrNull()?.let(Integer::valueOf) ?: 0
  return majorVersion >= 4
}
