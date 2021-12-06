// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName")

package org.jetbrains.kotlin.idea.gradleTooling.reflect

fun KotlinVariantReflection(variant: Any): KotlinVariantReflection =
    KotlinVariantReflectionImpl(variant)

interface KotlinVariantReflection : KotlinFragmentReflection {
    val variantAttributes: Map<String, String>?
    val compilationOutputs: KotlinCompilationOutputReflection?
}

private class KotlinVariantReflectionImpl(private val instance: Any) :
    KotlinVariantReflection,
    KotlinFragmentReflection by KotlinFragmentReflection(instance) {

    override val variantAttributes: Map<String, String>? by lazy {
        instance.callReflective("getVariantAttributes", parameters(), returnType<Map<Any, String>>(), logger)?.let { map ->
            map.mapKeys { (key, _) -> key.callReflective("getUniqueName", parameters(), returnType<String>(), logger) ?: return@lazy null }
        }
    }

    override val compilationOutputs: KotlinCompilationOutputReflection? by lazy {
        instance.callReflective("getCompilationOutputs", parameters(), returnType<Any>(), logger)?.let { output ->
            KotlinCompilationOutputReflection(output)
        }
    }

    companion object {
        val logger = ReflectionLogger(KotlinVariantReflection::class.java)
    }
}
