// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleJvmSupportMatrices")

package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix


fun isSupported(gradleVersion: GradleVersion, javaVersion: JavaVersion): Boolean {
  return GradleJvmSupportMatrix.INSTANCE.isSupported(gradleVersion, javaVersion)

}
fun suggestGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return GradleJvmSupportMatrix.INSTANCE.suggestGradleVersion(javaVersion)
}

fun suggestJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return GradleJvmSupportMatrix.INSTANCE.suggestJavaVersion(gradleVersion)
}

fun suggestOldestCompatibleGradleVersion(javaVersion: JavaVersion): GradleVersion? {
  return GradleJvmSupportMatrix.INSTANCE.suggestOldestCompatibleGradleVersion(javaVersion)
}

fun suggestOldestCompatibleJavaVersion(gradleVersion: GradleVersion): JavaVersion? {
  return GradleJvmSupportMatrix.INSTANCE.suggestOldestCompatibleJavaVersion(gradleVersion)
}
