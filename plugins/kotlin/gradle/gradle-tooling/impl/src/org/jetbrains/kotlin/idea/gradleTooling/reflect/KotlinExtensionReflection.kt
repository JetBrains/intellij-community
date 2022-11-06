// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Named
import org.gradle.api.Project

fun KotlinExtensionReflection(project: Project, kotlinExtension: Any): KotlinExtensionReflection {
    return KotlinExtensionReflectionImpl(project, kotlinExtension)
}

interface KotlinExtensionReflection {
    val kotlinExtension: Any
    val kotlinGradlePluginVersion: String?
    val targets: List<KotlinTargetReflection>
    val sourceSets: List<KotlinSourceSetReflection>
}

private class KotlinExtensionReflectionImpl(
    private val project: Project, override val kotlinExtension: Any
) : KotlinExtensionReflection {

    override val kotlinGradlePluginVersion: String? by lazy {
        Static(KOTLIN_PLUGIN_WRAPPER_FILE_CLASS_NAME, kotlinExtension.javaClass.classLoader, logger)
            ?.callReflective("getKotlinPluginVersion", parameters(parameter(project)), returnType<String>(), logger)
    }

    override val targets: List<KotlinTargetReflection> by lazy {
        kotlinExtension.callReflectiveGetter<Iterable<*>>("getTargets", logger)?.filterNotNull()
            ?.map { KotlinTargetReflection(it) }.orEmpty()
    }

    override val sourceSets: List<KotlinSourceSetReflection> by lazy {
        kotlinExtension.callReflectiveGetter<Iterable<*>>("getSourceSets", logger)
            ?.filterNotNull().orEmpty().mapNotNull { sourceSet ->
                if (sourceSet !is Named) {
                    logger.logIssue("KotlinSourceSet $sourceSet does not implement 'Named'")
                    return@mapNotNull null
                }
                sourceSet
            }
            .map { sourceSet -> KotlinSourceSetReflection(sourceSet) }
    }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KotlinExtensionReflection::class.java)
        private const val KOTLIN_PLUGIN_WRAPPER_FILE_CLASS_NAME = "org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt"
    }
}