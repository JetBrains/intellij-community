// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.IntellijInternalApi
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptAdditionalTask
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@IntellijInternalApi
val LOG = Logger.getInstance(KotlinDslScriptModelResolverCommon::class.java)

@Order(Integer.MIN_VALUE) // to be the first
abstract class KotlinDslScriptModelResolverCommon : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(KotlinDslScriptsModel::class.java)
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinDslScriptAdditionalTask::class.java)
    }

    override fun getExtraCommandLineArgs(): List<String> {
        return listOf("-P$CORRELATION_ID_GRADLE_PROPERTY_NAME=${System.nanoTime()}")
    }
}
