// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.KotlinLocalModuleIdentifierImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMavenModuleIdentifierImpl
import org.jetbrains.kotlin.idea.gradleTooling.get
import org.jetbrains.kotlin.idea.projectModel.KotlinLocalModuleIdentifier
import org.jetbrains.kotlin.idea.projectModel.KotlinMavenModuleIdentifier
import org.jetbrains.kotlin.idea.projectModel.KotlinModuleIdentifier

object KotlinModuleIdentifierBuilder : KotlinModelComponentBuilderBase<KotlinModuleIdentifier> {
    override fun buildComponent(origin: Any): KotlinModuleIdentifier? = when (origin.javaClass.name) {
        LOCAL_MODULE_IDENTIFIER_CLASS_NAME -> KotlinLocalModuleIdentifierBuilder.buildComponent(origin)
        MAVEN_MODULE_IDENTIFIER_CLASS_NAME -> KotlinMavenModuleIdentifierBuilder.buildComponent(origin)
        else -> {
            LOGGER.error("Unknown module identifier: \"${origin.javaClass.name}\"")
            null
        }
    }

    private val LOGGER = Logging.getLogger(KotlinModuleIdentifierBuilder.javaClass)

    private const val LOCAL_MODULE_IDENTIFIER_CLASS_NAME = "org.jetbrains.kotlin.project.model.LocalModuleIdentifier"
    private const val MAVEN_MODULE_IDENTIFIER_CLASS_NAME = "org.jetbrains.kotlin.project.model.MavenModuleIdentifier"

    private object KotlinLocalModuleIdentifierBuilder : KotlinModelComponentBuilderBase<KotlinLocalModuleIdentifier> {
        override fun buildComponent(origin: Any): KotlinLocalModuleIdentifier {
            val moduleClassifier = origin["getModuleClassifier"] as? String
            val buildId = origin["getBuildId"] as String
            val projectId = origin["getProjectId"] as String
            return KotlinLocalModuleIdentifierImpl(moduleClassifier, buildId, projectId)
        }
    }

    private object KotlinMavenModuleIdentifierBuilder : KotlinModelComponentBuilderBase<KotlinMavenModuleIdentifier> {
        override fun buildComponent(origin: Any): KotlinMavenModuleIdentifier {
            val moduleClassifier = origin["getModuleClassifier"] as? String
            val group = origin["getGroup"] as String
            val name = origin["getName"] as String
            return KotlinMavenModuleIdentifierImpl(moduleClassifier, group, name)
        }
    }
}