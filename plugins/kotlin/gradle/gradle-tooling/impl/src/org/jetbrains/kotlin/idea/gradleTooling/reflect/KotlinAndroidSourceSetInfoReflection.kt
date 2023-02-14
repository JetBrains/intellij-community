// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling.reflect

fun KotlinAndroidSourceSetInfoReflection(instance: Any): KotlinAndroidSourceSetInfoReflection {
    return KotlinAndroidSourceSetInfoReflectionImpl(instance)
}

interface KotlinAndroidSourceSetInfoReflection {
    val kotlinSourceSetName: String?
    val androidSourceSetName: String?
    val androidVariantNames: Set<String>?
}

private class KotlinAndroidSourceSetInfoReflectionImpl(
    private val instance: Any
) : KotlinAndroidSourceSetInfoReflection {

    override val kotlinSourceSetName: String? by lazy {
        instance.callReflectiveGetter<String>("getKotlinSourceSetName", logger)
    }

    override val androidSourceSetName: String? by lazy {
        instance.callReflectiveGetter<String>("getAndroidSourceSetName", logger)
    }

    override val androidVariantNames: Set<String>? by lazy {
        instance.callReflectiveGetter<Iterable<*>>("getAndroidVariantNames", logger)?.mapNotNull { it as? String }?.toSet()
    }

    companion object {
        val logger = ReflectionLogger(KotlinAndroidSourceSetInfoReflection::class.java)
    }
}