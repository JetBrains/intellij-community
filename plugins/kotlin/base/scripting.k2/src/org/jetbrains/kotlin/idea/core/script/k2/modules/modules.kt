// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getWorkspaceModelManager
import org.jetbrains.kotlin.idea.core.script.k2.configurations.scriptModuleRelativeLocation
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

interface ScriptWorkspaceModelManager {
    suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>)

    fun isModuleExist(
      project: Project, scriptFile: VirtualFile, definition: ScriptDefinition
    ): Boolean = project.workspaceModel.currentSnapshot.contains(getModuleId(project, scriptFile, definition))

    fun getModuleId(
      project: Project, scriptFile: VirtualFile, definition: ScriptDefinition
    ): ModuleId {
        val scriptModuleLocation = project.scriptModuleRelativeLocation(scriptFile)
        return ModuleId("${KOTLIN_SCRIPTS_MODULE_NAME}.${definition.name}.$scriptModuleLocation")
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

            val modulesToRemove = scripts.mapNotNull {
                val definition = findScriptDefinition(this, VirtualFileScriptSource(it))
                val workspaceModelManager = definition.getWorkspaceModelManager(this)
                workspaceModelManager.getModuleId(this, it, definition).resolve(currentSnapshot) ?: return@mapNotNull null
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