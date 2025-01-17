// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import java.nio.file.Path

@ApiStatus.NonExtendable
interface GradleSettingScriptBuilder<Self : GradleSettingScriptBuilder<Self>>
  : GradleSettingScriptBuilderCore<Self> {

  fun include(relativePath: Path): Self

  companion object {

    @JvmStatic
    fun create(useKotlinDsl: Boolean): GradleSettingScriptBuilder<*> {
      return create(GradleDsl.valueOf(useKotlinDsl))
    }

    @JvmStatic
    fun create(gradleDsl: GradleDsl): GradleSettingScriptBuilder<*> {
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GroovyDslGradleSettingScriptBuilder.Impl()
        GradleDsl.KOTLIN -> KotlinDslGradleSettingScriptBuilder.Impl()
      }
    }
  }
}