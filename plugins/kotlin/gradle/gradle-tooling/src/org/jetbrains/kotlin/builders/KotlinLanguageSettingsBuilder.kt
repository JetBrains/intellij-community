/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.builders

import org.jetbrains.kotlin.gradle.KotlinLanguageSettings
import org.jetbrains.kotlin.gradle.KotlinLanguageSettingsImpl
import org.jetbrains.kotlin.reflect.KotlinLanguageSettingsReflection

object KotlinLanguageSettingsBuilder : KotlinModelComponentBuilderBase<KotlinLanguageSettingsReflection, KotlinLanguageSettings> {
    override fun buildComponent(origin: KotlinLanguageSettingsReflection): KotlinLanguageSettings {
        @Suppress("UNCHECKED_CAST")
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
