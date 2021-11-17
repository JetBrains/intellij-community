// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.idea.gradleTooling.KotlinLanguageSettingsImpl
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.projectModel.KotlinLanguageSettings

object KotlinLanguageSettingsBuilder : KotlinModelComponentBuilderBase<KotlinLanguageSettings> {
    override fun buildComponent(origin: Any): KotlinLanguageSettings? {
        val languageSettingsClass = origin.javaClass
        val getLanguageVersion = languageSettingsClass.getMethodOrNull("getLanguageVersion") ?: return null
        val getApiVersion = languageSettingsClass.getMethodOrNull("getApiVersion") ?: return null
        val getProgressiveMode = languageSettingsClass.getMethodOrNull("getProgressiveMode") ?: return null
        val getEnabledLanguageFeatures = languageSettingsClass.getMethodOrNull("getEnabledLanguageFeatures") ?: return null
        val getOptInAnnotationsInUse = languageSettingsClass.getMethodOrNull("getOptInAnnotationsInUse")
        val getCompilerPluginArguments = languageSettingsClass.getMethodOrNull("getCompilerPluginArguments")
        val getCompilerPluginClasspath = languageSettingsClass.getMethodOrNull("getCompilerPluginClasspath")
        val getFreeCompilerArgs = languageSettingsClass.getMethodOrNull("getFreeCompilerArgs")
        @Suppress("UNCHECKED_CAST")
        return KotlinLanguageSettingsImpl(
            getLanguageVersion(origin) as? String,
            getApiVersion(origin) as? String,
            getProgressiveMode(origin) as? Boolean ?: false,
            getEnabledLanguageFeatures(origin) as? Set<String> ?: emptySet(),
            getOptInAnnotationsInUse?.invoke(origin) as? Set<String> ?: emptySet(),
            (getCompilerPluginArguments?.invoke(origin) as? List<String> ?: emptyList()).toTypedArray(),
            (getCompilerPluginClasspath?.invoke(origin) as? FileCollection)?.files ?: emptySet(),
            (getFreeCompilerArgs?.invoke(origin) as? List<String>).orEmpty().toTypedArray()
        )
    }
}