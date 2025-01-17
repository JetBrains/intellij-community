// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.util.GradleConstants
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
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GroovyDslGradleSettingScriptBuilder.Impl(gradleVersion)
        GradleDsl.KOTLIN -> KotlinDslGradleSettingScriptBuilder.Impl(gradleVersion)
      }
    }

    @JvmStatic
    fun settingsScript(gradleVersion: GradleVersion, gradleDsl: GradleDsl, configure: GradleSettingScriptBuilder<*>.() -> Unit): String {
      return create(gradleVersion, gradleDsl)
        .apply(configure)
        .generate()
    }

    @JvmStatic
    fun getSettingsScriptName(gradleDsl: GradleDsl): String {
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GradleConstants.SETTINGS_FILE_NAME
        GradleDsl.KOTLIN -> GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME
      }
    }
  }
}