// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import java.io.File
import kotlin.reflect.full.memberProperties

fun KotlinLanguageSettingsReflection(languageSettings: Any): KotlinLanguageSettingsReflection =
    KotlinLanguageSettingsReflectionImpl(languageSettings)

interface KotlinLanguageSettingsReflection {
    val languageVersion: String?
    val apiVersion: String?
    val progressiveMode: Boolean?
    val enabledLanguageFeatures: Set<String>?
    val optInAnnotationsInUse: Set<String>?
    val compilerPluginArguments: List<String>?
    val compilerPluginClasspath: Set<File>?
    val freeCompilerArgs: List<String>?
}

private class KotlinLanguageSettingsReflectionImpl(private val instance: Any) : KotlinLanguageSettingsReflection {
    override val languageVersion: String? by lazy {
        instance.callReflective("getLanguageVersion", parameters(), returnType<String?>(), logger)
    }

    override val apiVersion: String? by lazy {
        instance.callReflective("getApiVersion", parameters(), returnType<String?>(), logger)
    }

    override val progressiveMode: Boolean? by lazy {
        instance.callReflectiveGetter("getProgressiveMode", logger)
    }

    override val enabledLanguageFeatures: Set<String>? by lazy {
        instance.callReflective("getEnabledLanguageFeatures", parameters(), returnType<Iterable<String>>(), logger)?.toSet()
    }

    override val optInAnnotationsInUse: Set<String>? by lazy {
        val getterName = if (instance::class.memberProperties.any { it.name == "optInAnnotationsInUse" }) "getOptInAnnotationsInUse"
        else "getExperimentalAnnotationsInUse"
        instance.callReflective(getterName, parameters(), returnType<Iterable<String>>(), logger)?.toSet()
    }

    override val compilerPluginArguments: List<String>? by lazy {
        instance.callReflective("getCompilerPluginArguments", parameters(), returnType<Iterable<String>?>(), logger)?.toList()
    }

    override val compilerPluginClasspath: Set<File>? by lazy {
        instance.callReflective("getCompilerPluginClasspath", parameters(), returnType<Iterable<File>?>(), logger)?.toSet()
    }

    override val freeCompilerArgs: List<String>? by lazy {
        instance.callReflective("getFreeCompilerArgs", parameters(), returnType<Iterable<String>>(), logger)?.toList()
    }

    companion object {
        val logger = ReflectionLogger(KotlinLanguageSettingsReflection::class.java)
    }

}
