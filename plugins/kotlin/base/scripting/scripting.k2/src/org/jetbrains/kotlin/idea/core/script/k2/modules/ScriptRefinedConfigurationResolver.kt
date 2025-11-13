// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.KotlinScriptResolutionService.Companion.dropKotlinScriptCaches
import org.jetbrains.kotlin.idea.core.script.k2.toConfigurationResult
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult

interface ScriptConfigurationProviderExtension {
    fun get(project: Project, virtualFile: VirtualFile): ScriptCompilationConfigurationResult? =
        getScriptConfigurationFromWorkspaceModel(project, virtualFile)

    suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptCompilationConfigurationResult? = null

    fun remove(virtualFile: VirtualFile): Unit = Unit

    companion object {
        fun getScriptConfigurationFromWorkspaceModel(project: Project, virtualFile: VirtualFile): ScriptCompilationConfigurationResult? {
            val virtualFileUrl = virtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
            val entity = project.workspaceModel.currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl)
                .singleOrNull { it is KotlinScriptEntity } as? KotlinScriptEntity

            return entity?.toConfigurationResult()
        }
    }
}

suspend fun Project.updateKotlinScriptEntities(entitySource: EntitySource, updater: (MutableEntityStorage) -> Unit) {
    workspaceModel.update("updating kotlin script entities [$entitySource]") {
        updater(it)
        dropKotlinScriptCaches(this)
    }
}