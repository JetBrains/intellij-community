// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull

fun KotlinMultiplatformImportReflection(kotlin: KotlinExtensionReflection): KotlinMultiplatformImportReflection? {
    val classLoader = kotlin.kotlinExtension.javaClass.classLoader
    val clazz = classLoader.loadClassOrNull("org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport") ?: return null

    val instance = Static(clazz).callReflective(
        "instance", parameters(parameter(kotlin.project)), returnType<Any>(), KotlinMultiplatformImportReflectionImpl.logger
    ) ?: return null

    return KotlinMultiplatformImportReflectionImpl(instance)
}

interface KotlinMultiplatformImportReflection {
    fun resolveDependenciesSerialized(sourceSet: KotlinSourceSetReflection): Iterable<ByteArray>
}

class KotlinMultiplatformImportReflectionImpl(val instance: Any) : KotlinMultiplatformImportReflection {

    override fun resolveDependenciesSerialized(sourceSet: KotlinSourceSetReflection): Iterable<ByteArray> {
        return instance.callReflective(
            "resolveDependenciesSerialized", parameters(parameter(sourceSet.name)), returnType<Iterable<ByteArray?>>(), logger
        )?.filterNotNull().orEmpty()
    }

    companion object {
        val logger = ReflectionLogger(KotlinMultiplatformImportReflection::class.java)
    }
}