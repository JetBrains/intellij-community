// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
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
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.toBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.k2.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.scripting.definitions.isNonScript

class KotlinScriptFileListener : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
        val projects = ProjectManager.getInstance().getOpenProjects().filterNot { it.isDisposed }
        if (projects.isEmpty()) return null

        val scriptsToRemove = mutableSetOf<VirtualFile>()
        val scriptsToLoad = mutableSetOf<VirtualFile>()

        for (event in events) {
            when (event) {
                is VFileDeleteEvent -> {
                    if (event.file.isNonScript()) continue
                    scriptsToRemove += event.file
                }

                is VFileMoveEvent -> {
                    if (event.file.isNonScript()) continue
                    scriptsToRemove += event.file
                    scriptsToLoad += event.file
                }

                is VFilePropertyChangeEvent if (event.isRename) -> {
                    val oldValue = event.oldValue as? String
                    if (oldValue != null && oldValue.endsWith(".kts")) {
                        scriptsToRemove += event.file
                    }

                    val newValue = event.newValue as? String
                    if (newValue != null && newValue.endsWith(".kts")) {
                        scriptsToLoad += event.file
                    }
                }
            }
        }

        return object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() {
                if (scriptsToRemove.isEmpty()) return

                projects.forEach { project ->
                    KotlinScriptModuleManager.getInstance(project).removeScriptEntities(scriptsToRemove.toList())
                }
            }

            override fun afterVfsChange() {
                if (scriptsToLoad.isEmpty()) return

                projects.forEach { project ->
                    ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
                    if (scriptsToLoad.isNotEmpty()) {
                        scriptsToLoad.forEach(KotlinScriptService.getInstance(project)::scheduleLoading)
                    }
                }
            }
        }
    }
}

@Service(Service.Level.PROJECT)
class KotlinScriptModuleManager(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun removeScriptEntities(scripts: List<VirtualFile>) {
        val builder = project.workspaceModel.currentSnapshot.toBuilder()
        val fileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

        val modulesToRemove = scripts.flatMap {
            builder.getVirtualFileUrlIndex().findEntitiesByUrl(it.toVirtualFileUrl(fileUrlManager)).filterIsInstance<KotlinScriptEntity>()
        }

        if (modulesToRemove.isEmpty()) return

        coroutineScope.launch {
            project.workspaceModel.update("removing .kts modules") {
                modulesToRemove.forEach(it::removeEntity)
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinScriptModuleManager = project.service()
    }
}