// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.gradle.util.GradleVersion

fun getFoojayPluginVersion(): String {
  return "0.8.0"
}

fun isFoojayPluginSupported(gradleVersion: GradleVersion): Boolean {
  return GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.6")
}