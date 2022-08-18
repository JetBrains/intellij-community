// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.nullize
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener

class VfsCodeBlockModificationListener : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): ChangeApplier? {
        val openProjects = ProjectManager.getInstance().openProjects.nullize() ?: return null

        val relevantFiles = events.mapNotNull {
            ProgressManager.checkCanceled()
            it.takeIf { it.isFromRefresh || it is VFileContentChangeEvent }?.file
        }.nullize() ?: return null

        val changedProjects = openProjects.filter { project ->
            relevantFiles.any {
                ProgressManager.checkCanceled()
                RootKindFilter.projectSources.matches(project, it)
            }
        }.nullize() ?: return null

        return Applier(changedProjects)
    }

    private class Applier(private val changedProjects: List<Project>) : ChangeApplier {
        override fun afterVfsChange() {
            for (project in changedProjects) {
                if (project.isDisposed) continue
                KotlinCodeBlockModificationListener.getInstance(project).incModificationCount()
            }
        }
    }
}