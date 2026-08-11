// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.gradle.toolingExtension.modelProvider.GradleClassBuildModelProvider
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters.CORRELATION_ID_GRADLE_PROPERTY_NAME
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDslScriptAdditionalTask
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class KotlinDslScriptModelResolver : AbstractProjectResolverExtension() {

    override fun getModelProviders(): List<ProjectImportModelProvider> = listOf(
        GradleClassBuildModelProvider(KotlinDslScriptAdditionalTask::class.java, GradleModelFetchPhase.PROJECT_LOADED_PHASE),
        GradleClassBuildModelProvider(KotlinDslScriptsModel::class.java, GradleModelFetchPhase.SCRIPT_MODEL_PHASE)
    )

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(KotlinToolingVersion::class.java, KotlinDslScriptsModel::class.java)
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinToolingVersion::class.java, KotlinDslScriptAdditionalTask::class.java)
    }

    override fun getExtraCommandLineArgs(): List<String> {
        return listOf("-P$CORRELATION_ID_GRADLE_PROPERTY_NAME=${System.nanoTime()}")
    }
}

