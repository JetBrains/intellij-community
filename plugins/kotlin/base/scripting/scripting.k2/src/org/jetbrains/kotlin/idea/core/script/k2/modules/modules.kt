// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

interface ScriptWorkspaceModelManager {
    suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>)

    fun isScriptExist(
        project: Project, scriptFile: VirtualFile, definition: ScriptDefinition
    ): Boolean {
        val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
        val index = project.workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
        return index.findEntitiesByUrl(scriptFile.toVirtualFileUrl(fileUrlManager)).any()
    }
}

@Service(Service.Level.PROJECT)
class KotlinScriptModuleManager(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun removeScriptModules(scripts: List<VirtualFile>) {
        coroutineScope.launch {
            project.removeScriptModules(scripts)
        }
    }

    companion object {
        suspend fun Project.removeScriptModules(scripts: List<VirtualFile>) {
            val currentSnapshot = workspaceModel.currentSnapshot
            val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

            val modulesToRemove = scripts.flatMap {
                currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(it.toVirtualFileUrl(fileUrlManager))
            }

            if (modulesToRemove.isEmpty()) return

            workspaceModel.update("removing .kts modules") {
                modulesToRemove.forEach(it::removeEntity)
            }
        }

        @JvmStatic
        fun getInstance(project: Project): KotlinScriptModuleManager = project.service()
    }
}