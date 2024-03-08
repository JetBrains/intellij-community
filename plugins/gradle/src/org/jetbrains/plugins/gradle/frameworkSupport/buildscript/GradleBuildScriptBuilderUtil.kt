// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleBuildScriptBuilderUtil")

package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion


fun getKotlinVersion(gradleVersion: GradleVersion): String {
  return when {
    GradleVersionUtil.isGradleAtLeast(gradleVersion, "6.7.1") -> "1.7.20"
    GradleVersionUtil.isGradleAtLeast(gradleVersion, "5.6.2") -> "1.4.32"
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

fun isGroovyApacheSupported(groovyVersion: String): Boolean {
  val majorVersion = groovyVersion.split(".").firstOrNull()?.let(Integer::valueOf) ?: 0
  return majorVersion >= 4
}
