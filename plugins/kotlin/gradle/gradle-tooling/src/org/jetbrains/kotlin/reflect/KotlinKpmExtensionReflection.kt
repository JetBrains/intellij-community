/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.reflect

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
        instance.callReflectiveGetter("getCoreLibrariesVersion", logger)
    }

    override val explicitApiCliOption: String? by lazy {
        instance.callReflective("getExplicitApi", parameters(), returnType<Any?>(), logger)
            ?.callReflectiveGetter("getCliOption", logger)
    }

    override val modules: List<KotlinModuleReflection>? by lazy {
        instance.callReflective("getModules", parameters(), returnType<Iterable<Any>>(), logger)
            ?.map { module -> KotlinModuleReflection(module) }
    }

    companion object {
        val logger = ReflectionLogger(KotlinKpmExtensionReflection::class.java)
    }
}
