// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull
import org.jetbrains.kotlin.idea.gradleTooling.reflect.callReflective
import org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm.KotlinIdeFragmentDependencyResolverReflection.Companion.logger
import org.jetbrains.kotlin.idea.gradleTooling.reflect.parameter
import org.jetbrains.kotlin.idea.gradleTooling.reflect.parameters
import org.jetbrains.kotlin.idea.gradleTooling.reflect.returnType

interface KotlinIdeFragmentDependencyResolverReflection {
    fun resolveDependencies(fragment: KotlinFragmentReflection): List<KotlinIdeFragmentDependencyReflection>?

    companion object {
        val logger: Logger = Logging.getLogger(KotlinIdeFragmentDependencyResolverReflection::class.java)

        fun newInstance(project: Project, classLoader: ClassLoader): KotlinIdeFragmentDependencyResolverReflection? {
            val clazz = classLoader.loadClassOrNull(
                "org.jetbrains.kotlin.gradle.plugin.ide.IdeFragmentDependencyResolver"
            ) ?: run {
                logger.warn("Failed to find 'IdeFragmentDependencyResolver' class")
                return null
            }

            val createMethod = clazz.getMethodOrNull("create", Project::class.java) ?: run {
                logger.warn("IdeFragmentDependencyResolver.create method not found")
                return null
            }

            return IdeFragmentDependencyResolverReflectionImpl(createMethod.invoke(null, project))
        }
    }
}

private class IdeFragmentDependencyResolverReflectionImpl(private val instance: Any) : KotlinIdeFragmentDependencyResolverReflection {
    override fun resolveDependencies(fragment: KotlinFragmentReflection): List<KotlinIdeFragmentDependencyReflection>? {
        return instance.callReflective(
            "resolveDependencies",
            parameters(
                parameter<String>(fragment.containingModule?.name ?: return null),
                parameter<String>(fragment.fragmentName ?: return null)
            ),
            returnType<Iterable<Any>>(),
            logger
        )?.mapNotNull { dependency -> KotlinIdeFragmentDependencyReflection(dependency) }
    }
}
