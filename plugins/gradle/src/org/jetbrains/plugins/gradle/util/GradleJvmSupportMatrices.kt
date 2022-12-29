// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmSupportMatrices")

package org.jetbrains.plugins.gradle.util

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix


fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
  return GradleJvmSupportMatrix.getInstance().isSupported(gradleVersion, javaVersion)
}

fun getSupportedGradleVersions(javaVersion: JavaVersion): List<GradleVersion> {
  return GradleJvmSupportMatrix.getInstance().getSupportedGradleVersions(javaVersion)
}

fun getSupportedJavaVersions(gradleVersion: GradleVersion): List<JavaVersion> {
  return GradleJvmSupportMatrix.getInstance().getSupportedJavaVersions(gradleVersion)
}

fun suggestGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return GradleJvmSupportMatrix.getInstance().suggestGradleVersion(javaVersion)
}

fun suggestJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return GradleJvmSupportMatrix.getInstance().suggestJavaVersion(gradleVersion)
}

fun suggestOldestCompatibleGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return GradleJvmSupportMatrix.getInstance().suggestOldestCompatibleGradleVersion(javaVersion)
}

fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return GradleJvmSupportMatrix.getInstance().suggestOldestCompatibleJavaVersion(gradleVersion)
}
