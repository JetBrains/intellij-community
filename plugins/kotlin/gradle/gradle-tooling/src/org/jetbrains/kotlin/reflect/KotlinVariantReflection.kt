/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.reflect

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
        instance.callReflective("getVariantAttributes", parameters(), returnType<Map<Any, String>>(), logger)
            ?.mapNotNull { (key, value) ->
                key.callReflective("getUniqueName", parameters(), returnType<String>(), logger)?.let { it to value }
                    ?: return@mapNotNull null
            }?.toMap()
    }

    override val compilationOutputs: KotlinCompilationOutputReflection? by lazy {
        instance.callReflectiveAnyGetter("getCompilationOutputs", logger)?.let { output ->
            KotlinCompilationOutputReflection(output)
        }
    }

    companion object {
        val logger = ReflectionLogger(KotlinVariantReflection::class.java)
    }
}
