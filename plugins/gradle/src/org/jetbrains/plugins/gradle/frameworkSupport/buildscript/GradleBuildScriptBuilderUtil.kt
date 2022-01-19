// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleBuildScriptBuilderUtil")

package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion

fun isSupportedJavaLibraryPlugin(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.baseVersion >= GradleVersion.version("3.4")
}

fun isSupportedImplementationScope(gradleVersion: GradleVersion): Boolean {
  return isSupportedJavaLibraryPlugin(gradleVersion)
}

fun isSupportedRuntimeOnlyScope(gradleVersion: GradleVersion): Boolean {
  return isSupportedJavaLibraryPlugin(gradleVersion)
}

fun isSupportedTaskConfigurationAvoidance(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.baseVersion >= GradleVersion.version("4.9")
}

fun isSupportedJUnit5(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.baseVersion >= GradleVersion.version("4.7")
}

fun isSupportedKotlin4(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.baseVersion >= GradleVersion.version("5.6.2")
}

fun getKotlinVersion(gradleVersion: GradleVersion): String {
  return if (isSupportedKotlin4(gradleVersion)) "1.4.32" else "1.3.50"
}

fun getGroovyVersion(): String {
  return "3.0.5"
}

fun getJunit4Version(): String {
  return "4.12"
}

fun getJunit5Version(): String {
  return "5.8.1"
}
