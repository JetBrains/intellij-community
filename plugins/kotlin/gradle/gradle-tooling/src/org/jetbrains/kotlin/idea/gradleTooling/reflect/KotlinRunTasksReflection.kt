// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.jetbrains.kotlin.idea.gradleTooling.getMethodOrNull

interface KotlinRunTaskReflection {
    val taskName: String?
    val compilationName: String?

    companion object {
        val logger = ReflectionLogger(KotlinRunTaskReflection::class.java)
    }
}

interface KotlinTestRunTaskReflection : KotlinRunTaskReflection

private abstract class AbstractKotlinTestRunTaskReflection(private val instance: Any) : KotlinTestRunTaskReflection {
    override val taskName: String? by lazy {
        instance.callReflectiveGetter("getName", KotlinRunTaskReflection.logger)
    }
    override val compilationName: String? by lazy {
        if (instance.javaClass.getMethodOrNull("getCompilation") == null)
            null
        else instance.callReflectiveAnyGetter("getCompilation", KotlinRunTaskReflection.logger)
            ?.callReflectiveGetter("getCompilationName", KotlinRunTaskReflection.logger)
    }
}

private class KotlinTestRunTaskReflectionImpl(instance: Any) : AbstractKotlinTestRunTaskReflection(instance)

fun KotlinAndroidTestRunTaskReflection(androidUnitTestTask: Any): KotlinAndroidTestRunTaskReflection =
    KotlinAndroidTestRunTaskReflectionImpl(androidUnitTestTask)

interface KotlinAndroidTestRunTaskReflection : KotlinTestRunTaskReflection

private class KotlinAndroidTestRunTaskReflectionImpl(instance: Any) : AbstractKotlinTestRunTaskReflection(instance),
                                                                      KotlinAndroidTestRunTaskReflection

fun KotlinNativeMainRunTaskReflection(nativeMainRunTask: Any): KotlinNativeMainRunTaskReflection =
    KotlinNativeMainRunTaskReflectionImpl(nativeMainRunTask)

interface KotlinNativeMainRunTaskReflection : KotlinRunTaskReflection {
    val entryPoint: String?
    val debuggable: Boolean?
}

private class KotlinNativeMainRunTaskReflectionImpl(private val instance: Any) : KotlinNativeMainRunTaskReflection {
    override val taskName: String? by lazy {
        instance.callReflectiveGetter("getRunTaskName", KotlinRunTaskReflection.logger)
    }
    override val compilationName: String? by lazy {
        instance.callReflectiveAnyGetter("getCompilation", KotlinRunTaskReflection.logger)
            ?.callReflectiveGetter("getCompilationName", KotlinRunTaskReflection.logger)
    }
    override val entryPoint: String? by lazy {
        instance.callReflectiveGetter("getEntryPoint", KotlinRunTaskReflection.logger)
    }
    override val debuggable: Boolean? by lazy {
        instance.callReflectiveGetter("getDebuggable", KotlinRunTaskReflection.logger)
    }
}
