// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.recentFiles.shared.RecentFileKind

internal class RemovedRecentFilesListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null

    val filesToRemove = events.filterIsInstance<VFileDeleteEvent>()
                          .map { it.file }
                          .takeIf { it.isNotEmpty() }
                        ?: return null

    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        for (project in ProjectManager.getInstance().openProjects) {
          val projectFilesToRemove = filesToRemove.filter { file ->
            ProjectFileIndex.getInstance(project).isInContent(file)
          }

          val recentFilesModel = BackendRecentFilesModel.getInstance(project)
          recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_EDITED, projectFilesToRemove, false)
          recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED, projectFilesToRemove, false)
          recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, projectFilesToRemove, false)
        }
      }
    }
  }
}