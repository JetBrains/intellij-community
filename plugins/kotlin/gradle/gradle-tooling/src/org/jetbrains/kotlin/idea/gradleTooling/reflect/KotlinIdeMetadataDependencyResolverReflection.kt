// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinIdeFragmentDependencyResolverReflection.Companion.logger

interface KotlinIdeFragmentDependencyResolverReflection {
    fun resolveDependencies(fragment: KotlinFragmentReflection): List<KotlinIdeFragmentDependencyReflection>?

    companion object {
        val logger = ReflectionLogger(KotlinIdeFragmentDependencyResolverReflection::class.java)

        fun newInstance(project: Project, classLoader: ClassLoader): KotlinIdeFragmentDependencyResolverReflection? {
            val clazz = classLoader.loadClassOrNull(
                "org.jetbrains.kotlin.gradle.plugin.ide.IdeFragmentDependencyResolver"
            ) ?: run {
                logger.logIssue("Failed to find 'IdeFragmentDependencyResolver' class")
                return null
            }

            val createMethod = clazz.getMethodOrNull("create", Project::class.java) ?: run {
                logger.logIssue("IdeFragmentDependencyResolver.create method not found")
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
