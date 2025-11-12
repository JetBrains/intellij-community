// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptEvaluationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptingHostConfigurationEntity

data class GradleScriptDefinitionEntityId(val id: String) : SymbolicEntityId<GradleScriptDefinitionEntity> {
    override val presentableName: @NlsSafe String = id
}

interface GradleScriptDefinitionEntity : WorkspaceEntityWithSymbolicId {
    val definitionId: String
    val compilationConfiguration: ScriptCompilationConfigurationEntity
    val hostConfiguration: ScriptingHostConfigurationEntity
    val evaluationConfiguration: ScriptEvaluationConfigurationEntity?

    override val symbolicId: GradleScriptDefinitionEntityId
        get() = GradleScriptDefinitionEntityId(definitionId)
}

object KotlinGradleScriptEntitySource : EntitySource
