// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.util.GradleConstants

enum class GradleDsl {
  GROOVY,
  KOTLIN;

  companion object {

    fun valueOf(useKotlinDsl: Boolean): GradleDsl =
      if (useKotlinDsl) KOTLIN else GROOVY

    @JvmStatic
    val GradleDsl.settingsScriptName: String
      get() = when (this) {
        GROOVY -> GradleConstants.SETTINGS_FILE_NAME
        KOTLIN -> GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME
      }

    @JvmStatic
    val GradleDsl.buildScriptName: String
      get() = when (this) {
        GROOVY -> GradleConstants.DEFAULT_SCRIPT_NAME
        KOTLIN -> GradleConstants.KOTLIN_DSL_SCRIPT_NAME
      }
  }
}