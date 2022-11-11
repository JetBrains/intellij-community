// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinLanguageSettingsImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinLanguageSettingsReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinLanguageSettings

object KotlinLanguageSettingsBuilder : KotlinModelComponentBuilderBase<KotlinLanguageSettingsReflection, KotlinLanguageSettings> {
    override fun buildComponent(origin: KotlinLanguageSettingsReflection): KotlinLanguageSettings {
      return KotlinLanguageSettingsImpl(
          languageVersion = origin.languageVersion,
          apiVersion = origin.apiVersion,
          isProgressiveMode = origin.progressiveMode ?: false,
          enabledLanguageFeatures = origin.enabledLanguageFeatures.orEmpty(),
          optInAnnotationsInUse = origin.optInAnnotationsInUse.orEmpty(),
          compilerPluginArguments = origin.compilerPluginArguments.orEmpty().toTypedArray(),
          compilerPluginClasspath = origin.compilerPluginClasspath.orEmpty(),
          freeCompilerArgs = origin.freeCompilerArgs.orEmpty().toTypedArray()
      )
    }
}
