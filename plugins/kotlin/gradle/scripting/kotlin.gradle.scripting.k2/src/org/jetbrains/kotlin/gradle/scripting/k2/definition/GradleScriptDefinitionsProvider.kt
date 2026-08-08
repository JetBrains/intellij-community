// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.definition

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.shared.definition.ErrorGradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.idea.core.script.k2.deserialize
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class GradleScriptDefinitionsProvider(val project: Project) : ScriptDefinitionsProvider {
    override val id: String = "Gradle"

    override fun provideDefinitions(
        baseHostConfiguration: ScriptingHostConfiguration,
        loadedScriptDefinitions: List<ScriptDefinition>,
    ): List<ScriptDefinition> =
        project.workspaceModel.currentSnapshot.entities(GradleScriptDefinitionEntity::class.java).map {
            val compilationConfigurationData = it.compilationConfigurationData.deserialize()
            val hostConfiguration = it.hostConfiguration.deserialize()
            val definition = if (compilationConfigurationData == null || hostConfiguration == null) {
                ErrorGradleScriptDefinition()
            } else {
                GradleScriptDefinition(
                    compilationConfigurationData,
                    hostConfiguration,
                    it.evaluationConfiguration?.deserialize(),
                ).withIdeKeys()
            }

            ScriptDefinition(
                definition.compilationConfiguration,
                definition.evaluationConfiguration ?: ScriptEvaluationConfiguration.Default,
            )
        }.toList()
}
