// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull

fun KotlinCompilerArgumentsResolverReflection(project: Project, classLoader: ClassLoader): KotlinCompilerArgumentsResolverReflection? {
    val clazz = classLoader.loadClassOrNull("org.jetbrains.kotlin.gradle.plugin.ide.IdeCompilerArgumentsResolver") ?: return null

    val instance = Static(clazz).callReflective(
        "instance", parameters(parameter(project)), returnType<Any>(), KotlinMultiplatformImportReflectionImpl.logger
    ) ?: return null

    return KotlinCompilerArgumentsResolverReflectionImpl(instance)
}

interface KotlinCompilerArgumentsResolverReflection {
    fun resolveCompilerArguments(owner: Any): List<String>?
}

private class KotlinCompilerArgumentsResolverReflectionImpl(val instance: Any) : KotlinCompilerArgumentsResolverReflection {
    override fun resolveCompilerArguments(owner: Any): List<String>? {
        return instance.callReflective(
            "resolveCompilerArguments", parameters(parameter<Any>(owner)), returnType<List<String>?>(),
            KotlinMultiplatformImportReflectionImpl.logger
        )
    }
}
