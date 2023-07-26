// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Named
import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull
import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull

fun KotlinTargetReflection(kotlinTarget: Any): KotlinTargetReflection = KotlinTargetReflectionImpl(kotlinTarget)

interface KotlinTargetReflection {
    val targetName: String
    val presetName: String?
    val disambiguationClassifier: String?
    val platformType: String?
    val gradleTarget: Named
    val compilations: Collection<KotlinCompilationReflection>?
    val isMetadataTargetClass: Boolean

    val nativeMainRunTasks: Collection<KotlinNativeMainRunTaskReflection>?
    val artifactsTaskName: String?
    val konanArtifacts: Collection<Any>?
}

private class KotlinTargetReflectionImpl(private val instance: Any) : KotlinTargetReflection {
    override val gradleTarget: Named
        get() = instance as Named

    override val isMetadataTargetClass: Boolean by lazy {
        val metadataTargetClass =
            instance.javaClass.classLoader.loadClassOrNull("org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget")
        metadataTargetClass?.isInstance(instance) == true
    }

    override val targetName: String by lazy {
        gradleTarget.name
    }

    override val presetName: String? by lazy {
        instance.callReflectiveGetter<Any?>("getPreset", logger)
            ?.callReflectiveGetter("getName", logger)
    }

    override val disambiguationClassifier: String? by lazy {
        instance.callReflectiveGetter("getDisambiguationClassifier",logger)
    }

    override val platformType: String? by lazy {
        instance.callReflectiveAnyGetter("getPlatformType", logger)?.callReflectiveGetter("getName", logger)
    }

    override val compilations: Collection<KotlinCompilationReflection>? by lazy {
        instance.callReflective("getCompilations", parameters(), returnType<Iterable<Any>>(), logger)?.map { compilation ->
            KotlinCompilationReflection(compilation)
        }
    }

    override val nativeMainRunTasks: Collection<KotlinNativeMainRunTaskReflection>? by lazy {
        val executableClass = instance.javaClass.classLoader.loadClassOrNull(EXECUTABLE_CLASS) ?: return@lazy null
        instance.callReflective("getBinaries", parameters(), returnType<Iterable<Any>>(), logger)
            ?.filterIsInstance(executableClass)
            ?.map { KotlinNativeMainRunTaskReflection(it) }
    }

    override val artifactsTaskName: String? by lazy {
        instance.callReflectiveGetter("getArtifactsTaskName", logger)
    }

    override val konanArtifacts: Collection<Any>? by lazy {
        if (!instance.javaClass.classLoader.loadClass(KOTLIN_NATIVE_TARGET_CLASS).isInstance(instance))
            null
        else
            instance.callReflective("getBinaries", parameters(), returnType<Iterable<Any?>>(), logger)?.filterNotNull()
    }

    companion object {
        private val logger = ReflectionLogger(KotlinTargetReflection::class.java)
        private const val EXECUTABLE_CLASS = "org.jetbrains.kotlin.gradle.plugin.mpp.Executable"
        private const val KOTLIN_NATIVE_TARGET_CLASS = "org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget"
    }
}
