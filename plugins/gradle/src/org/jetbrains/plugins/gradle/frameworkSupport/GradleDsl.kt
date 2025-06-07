// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

enum class GradleDsl {
  GROOVY,
  KOTLIN;

  companion object {

    fun valueOf(useKotlinDsl: Boolean): GradleDsl {
      return if (useKotlinDsl) KOTLIN else GROOVY
    }
  }
}