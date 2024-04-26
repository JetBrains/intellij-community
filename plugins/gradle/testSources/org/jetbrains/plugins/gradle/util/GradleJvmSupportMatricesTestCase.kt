// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilitySupportUpdater
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix

abstract class GradleJvmSupportMatricesTestCase : LightIdeaTestCase() {
  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().replaceService(GradleCompatibilitySupportUpdater::class.java,
                                                       object : GradleCompatibilitySupportUpdater() {
                                                         override fun checkForUpdates() {
                                                         }
                                                       }, testRootDisposable)
  }

  fun isSupported(gradleVersion: String, javaVersion: Int) =
    GradleJvmSupportMatrix.isSupported(GradleVersion.version(gradleVersion), JavaVersion.compose(javaVersion))

  fun suggestGradleVersion(javaVersion: Int): String? =
    suggestGradleVersion {
      withJavaVersionFilter(JavaVersion.compose(javaVersion))
    }?.version

  fun suggestLatestSupportedGradleVersion(javaVersion: Int): String? =
    GradleJvmSupportMatrix.suggestLatestSupportedGradleVersion(JavaVersion.compose(javaVersion))?.version

  fun suggestLatestSupportedJavaVersion(gradleVersion: String): Int? =
    GradleJvmSupportMatrix.suggestLatestSupportedJavaVersion(GradleVersion.version(gradleVersion))?.feature

  fun suggestOldestSupportedGradleVersion(javaVersion: Int): String? =
    GradleJvmSupportMatrix.suggestOldestSupportedGradleVersion(JavaVersion.compose(javaVersion))?.version

  fun suggestOldestSupportedJavaVersion(gradleVersion: String): Int? =
    GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(GradleVersion.version(gradleVersion))?.feature
}