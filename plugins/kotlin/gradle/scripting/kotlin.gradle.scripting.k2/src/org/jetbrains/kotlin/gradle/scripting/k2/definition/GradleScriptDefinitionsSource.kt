// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.definition

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleScriptDefinitionEntity
import org.jetbrains.kotlin.gradle.scripting.shared.definition.ErrorGradleScriptDefinition
import org.jetbrains.kotlin.gradle.scripting.shared.definition.GradleScriptDefinition
import org.jetbrains.kotlin.idea.core.script.k2.deserialize
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource

class GradleScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = project.workspaceModel.currentSnapshot.entities(GradleScriptDefinitionEntity::class.java).map {
            val compilationConfiguration = it.compilationConfiguration.deserialize()
            val hostConfiguration = it.hostConfiguration.deserialize()
            if (compilationConfiguration == null || hostConfiguration == null) {
                ErrorGradleScriptDefinition()
            } else {
                GradleScriptDefinition(
                    compilationConfiguration,
                    hostConfiguration,
                    it.evaluationConfiguration?.deserialize(),
                ).withIdeKeys(project)
            }
        }
}