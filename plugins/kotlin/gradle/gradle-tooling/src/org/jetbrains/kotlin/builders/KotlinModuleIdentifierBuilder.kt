/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.builders

import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.reflect.KotlinLocalModuleIdentifierReflection
import org.jetbrains.kotlin.reflect.KotlinMavenModuleIdentifierReflection
import org.jetbrains.kotlin.reflect.KotlinModuleIdentifierReflection

object KotlinModuleIdentifierBuilder : KotlinModelComponentBuilderBase<KotlinModuleIdentifierReflection, KotlinModuleIdentifier> {
    override fun buildComponent(origin: KotlinModuleIdentifierReflection): KotlinModuleIdentifier? = when (origin) {
        is KotlinLocalModuleIdentifierReflection -> KotlinLocalModuleIdentifierBuilder.buildComponent(origin)
        is KotlinMavenModuleIdentifierReflection -> KotlinMavenModuleIdentifierBuilder.buildComponent(origin)
        else -> {
            LOGGER.error("Unknown module identifier reflection: \"${origin.javaClass.name}\"")
            null
        }
    }

    private val LOGGER = Logging.getLogger(KotlinModuleIdentifierBuilder.javaClass)


    private object KotlinLocalModuleIdentifierBuilder :
        KotlinModelComponentBuilderBase<KotlinLocalModuleIdentifierReflection, KotlinLocalModuleIdentifier> {
        override fun buildComponent(origin: KotlinLocalModuleIdentifierReflection): KotlinLocalModuleIdentifier? {
            return KotlinLocalModuleIdentifierImpl(
                moduleClassifier = origin.moduleClassifier,
                buildId = origin.buildId ?: return null,
                projectId = origin.projectId ?: return null
            )
        }
    }

    private object KotlinMavenModuleIdentifierBuilder :
        KotlinModelComponentBuilderBase<KotlinMavenModuleIdentifierReflection, KotlinMavenModuleIdentifier> {
        override fun buildComponent(origin: KotlinMavenModuleIdentifierReflection): KotlinMavenModuleIdentifier? {
            return KotlinMavenModuleIdentifierImpl(
                moduleClassifier = origin.moduleClassifier,
                group = origin.group ?: return null,
                name = origin.name ?: return null
            )
        }
    }
}
