// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.k2.toConfigurationResult
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult

interface ScriptConfigurationProviderExtension {
    val project: Project

    fun getConfiguration(virtualFile: VirtualFile): ScriptCompilationConfigurationResult? =
        getConfigurationFromWorkspaceModel(virtualFile, project)

    suspend fun createConfiguration(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptCompilationConfigurationResult?

    fun removeConfiguration(virtualFile: VirtualFile): Unit = Unit

    companion object {
        private fun getConfigurationFromWorkspaceModel(
            virtualFile: VirtualFile,
            project: Project
        ): ScriptCompilationConfigurationResult? {
            val virtualFileUrl = virtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
            val entity = project.workspaceModel.currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl)
                .filterIsInstance<KotlinScriptEntity>().singleOrNull()
            return entity?.toConfigurationResult()
        }
    }
}

suspend fun Project.updateKotlinScriptEntities(entitySource: EntitySource, updater: (MutableEntityStorage) -> Unit) {
    workspaceModel.update("updating kotlin script entities [$entitySource]") {
        updater(it)
    }
}