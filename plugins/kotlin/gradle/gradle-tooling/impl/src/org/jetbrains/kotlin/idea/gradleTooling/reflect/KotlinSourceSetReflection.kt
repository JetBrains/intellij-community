// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Named
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware

fun KotlinSourceSetReflection(kotlinSourceSet: Named): KotlinSourceSetReflection {
    return KotlinSourceSetReflectionImpl(kotlinSourceSet)
}

interface KotlinSourceSetReflection {
    val instance: Named
    val name: String
    val languageSettings: KotlinLanguageSettingsReflection?
    val kotlin: SourceDirectorySet?
    val resources: SourceDirectorySet?
    val dependsOn: Set<KotlinSourceSetReflection>
    val additionalVisibleSourceSets: Set<KotlinSourceSetReflection>
    val androidSourceSetInfo: KotlinAndroidSourceSetInfoReflection?
}

private class KotlinSourceSetReflectionImpl(override val instance: Named) : KotlinSourceSetReflection {
    override val name: String get() = instance.name

    override val languageSettings: KotlinLanguageSettingsReflection? by lazy {
        instance.callReflectiveAnyGetter("getLanguageSettings", logger)
            ?.let { KotlinLanguageSettingsReflection(it) }
    }

    override val kotlin: SourceDirectorySet? by lazy {
        instance.callReflectiveGetter("getKotlin", logger)
    }

    override val resources: SourceDirectorySet? by lazy {
        instance.callReflectiveGetter("getResources", logger)
    }

    override val dependsOn: Set<KotlinSourceSetReflection> by lazy {
        instance.callReflectiveGetter<Iterable<Named>>("getDependsOn", logger)?.map {
            KotlinSourceSetReflection(it)
        }.orEmpty().toSet()
    }

    override val additionalVisibleSourceSets: Set<KotlinSourceSetReflection> by lazy {
        instance.callReflectiveGetter<Iterable<Named>>("getAdditionalVisibleSourceSets", logger)?.map {
            KotlinSourceSetReflection(it)
        }.orEmpty().toSet()
    }

    override val androidSourceSetInfo: KotlinAndroidSourceSetInfoReflection? by lazy {
        if (instance !is ExtensionAware) {
            logger.logIssue("KotlinSourceSet $instance does not implement 'ExtensionAware'")
            return@lazy null
        }

        val instance = instance.extensions.findByName("androidSourceSetInfo") ?: return@lazy null
        KotlinAndroidSourceSetInfoReflection(instance)
    }

    companion object {
        val logger = ReflectionLogger(KotlinSourceSetReflection::class.java)
    }
}