// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.settingsScriptName
import java.nio.file.Path

@ApiStatus.NonExtendable
interface GradleSettingScriptBuilder<Self : GradleSettingScriptBuilder<Self>>
  : GradleSettingScriptBuilderCore<Self> {

  fun include(relativePath: Path): Self

  fun withFoojayPlugin(): Self

  companion object {

    @JvmStatic
    fun create(gradleVersion: GradleVersion, useKotlinDsl: Boolean): GradleSettingScriptBuilder<*> {
      return create(gradleVersion, GradleDsl.valueOf(useKotlinDsl))
    }

    @JvmStatic
    fun create(gradleVersion: GradleVersion, gradleDsl: GradleDsl): GradleSettingScriptBuilder<*> {
      return GradleSettingScriptBuilderImpl(gradleVersion, gradleDsl)
    }

    @JvmStatic
    fun settingsScript(gradleVersion: GradleVersion, gradleDsl: GradleDsl, configure: GradleSettingScriptBuilder<*>.() -> Unit): String {
      return create(gradleVersion, gradleDsl)
        .apply(configure)
        .generate()
    }

    @JvmStatic
    @Deprecated("Use GradleDsl.settingsScriptName instead", ReplaceWith(
        "gradleDsl.settingsScriptName",
        "org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.settingsScriptName"
    ))
    fun getSettingsScriptName(gradleDsl: GradleDsl): String {
      return gradleDsl.settingsScriptName
    }
  }
}