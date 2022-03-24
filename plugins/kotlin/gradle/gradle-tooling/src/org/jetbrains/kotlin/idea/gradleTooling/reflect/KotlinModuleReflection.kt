// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect

import org.jetbrains.kotlin.idea.gradleTooling.loadClassOrNull

fun KotlinModuleReflection(module: Any): KotlinModuleReflection =
    KotlinModuleReflectionImpl(module)

interface KotlinModuleReflection {
    val name: String?
    val moduleClassifier: String?
    val moduleIdentifier: KotlinModuleIdentifierReflection?
    val fragments: List<KotlinFragmentReflection>?
    val variants: List<KotlinVariantReflection>?
}

private class KotlinModuleReflectionImpl(private val instance: Any) : KotlinModuleReflection {
    override val name: String? by lazy {
        instance.callReflectiveGetter("getName", logger)
    }

    override val moduleClassifier: String? by lazy {
        instance.callReflective("getModuleClassifier", parameters(), returnType<String?>(), logger)
    }

    override val moduleIdentifier: KotlinModuleIdentifierReflection? by lazy {
        instance.callReflectiveAnyGetter("getModuleIdentifier", logger)?.let { moduleIdentifier ->
            KotlinModuleIdentifierReflection(moduleIdentifier)
        }
    }

    override val fragments: List<KotlinFragmentReflection>? by lazy {
        instance.callReflective("getFragments", parameters(), returnType<Iterable<Any>>(), logger)
            ?.map { fragment ->
                when {
                    variantClass?.isInstance(fragment) == true -> KotlinVariantReflection(fragment)
                    fragmentClass?.isInstance(fragment) == true  -> KotlinFragmentReflection(fragment)
                    else -> error("Unknown fragment with type: ${fragment.javaClass.name}")
                }
            }
    }

    override val variants: List<KotlinVariantReflection>? by lazy {
        instance.callReflective("getVariants", parameters(), returnType<Iterable<Any>>(), logger)
            ?.map { variant -> KotlinVariantReflection(variant) }
    }

    private val variantClass = instance.javaClass.classLoader.loadClassOrNull(KOTLIN_GRADLE_VARIANT_CLASS_NAME)
    private val fragmentClass = instance.javaClass.classLoader.loadClassOrNull(KOTLIN_GRADLE_FRAGMENT_CLASS_NAME)

    companion object {
        val logger = ReflectionLogger(KotlinModuleReflection::class.java)
        private const val KOTLIN_GRADLE_FRAGMENT_CLASS_NAME = "org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinGradleFragment"
        private const val KOTLIN_GRADLE_VARIANT_CLASS_NAME = "org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinGradleVariant"
    }
}
