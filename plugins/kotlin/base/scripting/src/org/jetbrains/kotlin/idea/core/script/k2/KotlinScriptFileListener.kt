// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.workspaceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition

import org.jetbrains.kotlin.scripting.definitions.isNonScript
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource

class KotlinScriptFileListener : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val scripts = events.filter { it is VFileDeleteEvent || it is VFileMoveEvent || it is VFilePropertyChangeEvent && it.isRename }
            .mapNotNull { it.file }.filterNot { it.isNonScript() }.distinct()
        if (scripts.isEmpty()) return null

        val projects = ProjectManager.getInstance().getOpenProjects().filterNot { it.isDisposed }
        if (projects.isEmpty()) return null

        return object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() {
                projects.forEach { project ->
                    KotlinScriptFileChangeProcessor.getInstance(project).removeScriptModules(scripts)
                }
            }

            override fun afterVfsChange() {
                projects.forEach {
                    ScriptDependenciesModificationTracker.getInstance(it).incModificationCount()
                }
            }
        }
    }
}

@Service(Service.Level.PROJECT)
private class KotlinScriptFileChangeProcessor(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun removeScriptModules(scripts: List<VirtualFile>) {
        val currentSnapshot = project.workspaceModel.currentSnapshot

        val modulesToRemove = scripts.mapNotNull {
            val definition = findScriptDefinition(project, VirtualFileScriptSource(it))
            val workspaceModelManager = definition.getWorkspaceModelManager(project)
            workspaceModelManager.getModuleId(project, it, definition).resolve(currentSnapshot) ?: return@mapNotNull null
        }

        if (modulesToRemove.isEmpty()) return

        coroutineScope.launch {
            project.workspaceModel.update("updating .kts modules") {
                modulesToRemove.forEach(it::removeEntity)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinScriptFileChangeProcessor = project.service()
    }
}