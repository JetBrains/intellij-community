// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.KotlinLocalModuleIdentifierImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMavenModuleIdentifierImpl
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinLocalModuleIdentifierReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinMavenModuleIdentifierReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinModuleIdentifierReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinLocalModuleIdentifier
import org.jetbrains.kotlin.idea.projectModel.KotlinMavenModuleIdentifier
import org.jetbrains.kotlin.idea.projectModel.KotlinModuleIdentifier

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
