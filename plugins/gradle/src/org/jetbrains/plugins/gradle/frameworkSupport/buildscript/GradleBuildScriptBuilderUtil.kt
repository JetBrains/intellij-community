// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

fun isSupportedJUnit5(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.baseVersion >= GradleVersion.version("4.7")
}

fun isSupportedKotlin4(gradleVersion: GradleVersion): Boolean {
  return gradleVersion.baseVersion >= GradleVersion.version("5.6.2")
}
