// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("RemoveExplicitTypeArguments")

package org.jetbrains.kotlin.idea.gradleTooling.reflect.kpm

import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.reflect.callReflective
import org.jetbrains.kotlin.idea.gradleTooling.reflect.parameters
import org.jetbrains.kotlin.idea.gradleTooling.reflect.returnType

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
        instance.callReflective("getName", parameters(), returnType<String>(), logger)
    }

    override val moduleClassifier: String? by lazy {
        instance.callReflective("getModuleClassifier", parameters(), returnType<String?>(), logger)
    }

    override val moduleIdentifier: KotlinModuleIdentifierReflection? by lazy {
        instance.callReflective("getModuleIdentifier", parameters(), returnType<Any>(), logger)?.let { moduleIdentifier ->
            KotlinModuleIdentifierReflection(moduleIdentifier)
        }
    }

    override val fragments: List<KotlinFragmentReflection>? by lazy {
        instance.callReflective("getFragments", parameters(), returnType<Iterable<Any>>(), logger)?.let { fragments ->
            fragments.map { fragment -> KotlinFragmentReflection(fragment) }
        }
    }

    override val variants: List<KotlinVariantReflection>? by lazy {
        instance.callReflective("getVariants", parameters(), returnType<Iterable<Any>>(), logger)?.let { variants ->
            variants.map { variant -> KotlinVariantReflection(variant) }
        }
    }


    companion object {
        val logger = Logging.getLogger(KotlinModuleReflection::class.java)
    }
}
