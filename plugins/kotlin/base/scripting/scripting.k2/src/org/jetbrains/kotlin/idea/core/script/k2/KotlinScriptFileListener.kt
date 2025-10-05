// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptModuleManager
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.scripting.definitions.isNonScript

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
                    KotlinScriptModuleManager.getInstance(project).removeScriptModules(scripts)
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
