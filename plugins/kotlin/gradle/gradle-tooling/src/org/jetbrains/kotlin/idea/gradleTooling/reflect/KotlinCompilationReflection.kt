// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.gradle.api.Named

fun KotlinCompilationReflection(kotlinCompilation: Any): KotlinCompilationReflection =
    KotlinCompilationReflectionImpl(kotlinCompilation)

interface KotlinCompilationReflection {
    val compilationName: String?
    val kotlinGradleSourceSets: Collection<Named>?
    val compilationOutput: KotlinCompilationOutputReflection?
    val konanTargetName: String?
    val compileKotlinTaskName: String?
}

private class KotlinCompilationReflectionImpl(private val instance: Any) : KotlinCompilationReflection {
    override val compilationName: String? by lazy { instance.callReflectiveGetter("getName", logger) }
    override val kotlinGradleSourceSets: Collection<Named>? by lazy { instance.callReflectiveGetter("getKotlinSourceSets", logger) }
    override val compilationOutput: KotlinCompilationOutputReflection? by lazy {
        instance.callReflectiveAnyGetter("getOutput", logger)?.let { gradleOutput -> KotlinCompilationOutputReflection(gradleOutput) }
    }

    // Get konanTarget (for native compilations only).
    override val konanTargetName: String? by lazy {
        if (!instance.javaClass.classLoader.loadClass(NATIVE_COMPILATION_CLASS).isInstance(instance))
            null
        else
            instance.callReflectiveAnyGetter("getKonanTarget", logger)
                ?.callReflectiveGetter("getName", logger)
    }
    override val compileKotlinTaskName: String? by lazy { instance.callReflectiveGetter("getCompileKotlinTaskName", logger) }

    companion object {
        private val logger: ReflectionLogger = ReflectionLogger(KotlinCompilationReflection::class.java)
        private const val NATIVE_COMPILATION_CLASS = "org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinNativeCompilation"
    }
}