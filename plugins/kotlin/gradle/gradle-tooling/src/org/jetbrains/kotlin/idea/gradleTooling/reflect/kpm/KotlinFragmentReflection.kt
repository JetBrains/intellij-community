// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName")

package org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.reflect.callReflective
import org.jetbrains.kotlin.idea.gradleTooling.reflect.parameters
import org.jetbrains.kotlin.idea.gradleTooling.reflect.returnType

fun KotlinFragmentReflection(fragment: Any): KotlinFragmentReflection =
    KotlinFragmentReflectionImpl(fragment)

interface KotlinFragmentReflection {
    val fragmentName: String?
    val containingModule: KotlinModuleReflection?
    val kotlinSourceSourceRoots: SourceDirectorySet?
    val directRefinesDependencies: List<KotlinFragmentReflection>?
    val languageSettings: KotlinLanguageSettingsReflection?
}

private class KotlinFragmentReflectionImpl(
    private val instance: Any
) : KotlinFragmentReflection {
    override val fragmentName: String? by lazy {
        instance.callReflective("getFragmentName", parameters(), returnType<String>(), logger)
    }

    override val containingModule: KotlinModuleReflection? by lazy {
        instance.callReflective("getContainingModule", parameters(), returnType<Any>(), logger)?.let { module ->
            KotlinModuleReflection(module)
        }
    }

    override val kotlinSourceSourceRoots: SourceDirectorySet? by lazy {
        instance.callReflective("getKotlinSourceRoots", parameters(), returnType<SourceDirectorySet>(), logger)
    }

    override val directRefinesDependencies: List<KotlinFragmentReflection>? by lazy {
        instance.callReflective("getDirectRefinesDependencies", parameters(), returnType<Iterable<Any>>(), logger)?.let { fragments ->
            fragments.map { fragment -> KotlinFragmentReflection(fragment) }
        }
    }

    override val languageSettings: KotlinLanguageSettingsReflection? by lazy {
        instance.callReflective("getLanguageSettings", parameters(), returnType<Any>(), logger)?.let { languageSettings ->
            KotlinLanguageSettingsReflection(languageSettings)
        }
    }

    companion object {
        val logger = Logging.getLogger(KotlinFragmentReflection::class.java)
    }
}
