// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import org.jetbrains.plugins.gradle.codeInsight.GradlePluginDescriptionsExtension

internal class KotlinGradlePluginDescriptions : GradlePluginDescriptionsExtension {
  override fun getPluginDescriptions(): Map<String, String> = mapOf(
    "org.jetbrains.kotlin.kapt" to "Kotlin annotation processing plugin",
  )
}
