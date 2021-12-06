// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName")

package org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm

import org.jetbrains.kotlin.idea.gradleTooling.reflect.ReflectionLogger
import org.jetbrains.kotlin.idea.gradleTooling.reflect.callReflective
import org.jetbrains.kotlin.idea.gradleTooling.reflect.parameters
import org.jetbrains.kotlin.idea.gradleTooling.reflect.returnType

fun KotlinKpmExtensionReflection(extension: Any): KotlinKpmExtensionReflection =
    KotlinKpmExtensionReflectionImpl(extension)

interface KotlinKpmExtensionReflection {
    val coreLibrariesVersion: String?
    val explicitApiCliOption: String?
    val modules: List<KotlinModuleReflection>?
}

private class KotlinKpmExtensionReflectionImpl(
    private val instance: Any
) : KotlinKpmExtensionReflection {

    override val coreLibrariesVersion: String? by lazy {
        instance.callReflective("getCoreLibrariesVersion", parameters(), returnType<String>(), logger)
    }

    override val explicitApiCliOption: String? by lazy {
        instance.callReflective("getExplicitApi", parameters(), returnType<Any?>(), logger)
            ?.callReflective("getCliOption", parameters(), returnType<String>(), logger)
    }

    override val modules: List<KotlinModuleReflection>? by lazy {
        instance.callReflective("getModules", parameters(), returnType<Iterable<Any>>(), logger)?.let { modules ->
            modules.map { module -> KotlinModuleReflection(module) }
        }
    }

    companion object {
        val logger = ReflectionLogger(KotlinKpmExtensionReflection::class.java)
    }
}
