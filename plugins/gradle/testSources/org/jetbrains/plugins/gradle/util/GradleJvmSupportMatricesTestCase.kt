// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleCompatibilitySupportUpdater
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future


abstract class GradleJvmSupportMatricesTestCase : LightIdeaTestCase() {

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().replaceService(GradleCompatibilitySupportUpdater::class.java, object : GradleCompatibilitySupportUpdater() {
      override fun checkForUpdates(): Future<*> {
        return CompletableFuture.completedFuture(null)
      }
    }, testRootDisposable)
  }

  fun isSupported(gradleVersion: String, javaVersion: Int) =
    isSupported(GradleVersion.version(gradleVersion), JavaVersion.compose(javaVersion))

  fun suggestGradleVersion(javaVersion: Int): String? =
    suggestLatestGradleVersion(JavaVersion.compose(javaVersion))?.version

  fun suggestJavaVersion(gradleVersion: String): Int? =
    suggestLatestJavaVersion(GradleVersion.version(gradleVersion))?.feature

  fun suggestOldestCompatibleGradleVersion(javaVersion: Int): String? =
    suggestOldestCompatibleGradleVersion(JavaVersion.compose(javaVersion))?.version

  fun suggestOldestCompatibleJavaVersion(gradleVersion: String): Int? =
    suggestOldestCompatibleJavaVersion(GradleVersion.version(gradleVersion))?.feature
}