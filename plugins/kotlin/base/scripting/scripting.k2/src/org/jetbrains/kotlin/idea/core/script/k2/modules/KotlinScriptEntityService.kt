// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.idea.core.script.k2.asCompilationConfiguration
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.ResultWithDiagnostics

@Service(Service.Level.PROJECT)
internal class KotlinScriptEntityService(val project: Project) {
    fun getConfigurationResult(entity: KotlinScriptEntity): ScriptCompilationConfigurationResult? {
        val virtualFile = entity.virtualFileUrl.virtualFile ?: return null
        val snapshot = project.workspaceModel.currentSnapshot
        val diagnostics = entity.reports.map { report -> report.map() }
        val configurationEntity =
            entity.configuration?.let { snapshot.resolve(it) } ?: return null

        return ResultWithDiagnostics.Success(
            ScriptCompilationConfigurationWrapper(VirtualFileScriptSource(virtualFile), configurationEntity.data.asCompilationConfiguration()),
            diagnostics
        )
    }

    companion object {
        fun getConfigurationResult(project: Project, entity: KotlinScriptEntity) =
            project.service<KotlinScriptEntityService>().getConfigurationResult(entity)
    }
}
