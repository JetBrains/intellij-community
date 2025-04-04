// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.recentFiles.shared.RecentFileKind

internal class RemovedRecentFilesListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null

    val removedFiles = events.filterIsInstance<VFileDeleteEvent>()
      .map { it.file }
    val movedFiles = events.filterIsInstance<VFileMoveEvent>()
      .map { it.file }
    val renamedFiles = events.filterIsInstance<VFilePropertyChangeEvent>()
      .filter { it.propertyName == "name" }
      .map { it.file }

    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        for (project in ProjectManager.getInstance().openProjects) {
          val recentFilesModel = BackendRecentFilesModel.getInstance(project)

          val projectFilesToRemove = removedFiles.filter { file ->
            ProjectFileIndex.getInstance(project).isInContent(file)
          }
          if (projectFilesToRemove.isNotEmpty()) {
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_EDITED, FileChangeKind.REMOVED, projectFilesToRemove)
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED, FileChangeKind.REMOVED, projectFilesToRemove)
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, FileChangeKind.REMOVED, projectFilesToRemove)
          }

          val projectFilesToUpdate = movedFiles.filter { file ->
            ProjectFileIndex.getInstance(project).isInContent(file)
          }
          if (projectFilesToUpdate.isNotEmpty()) {
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_EDITED, FileChangeKind.UPDATED, projectFilesToUpdate)
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED, FileChangeKind.UPDATED, projectFilesToUpdate)
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, FileChangeKind.UPDATED, projectFilesToUpdate)
          }

          val projectFilesToRename = renamedFiles.filter { file ->
            file.isValid && ProjectFileIndex.getInstance(project).isInContent(file)
          }
          recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_EDITED, FileChangeKind.REMOVED, projectFilesToRename)
          recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED, FileChangeKind.REMOVED, projectFilesToRename)
          recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, FileChangeKind.REMOVED, projectFilesToRename)
        }
      }

      override fun afterVfsChange() {
        for (project in ProjectManager.getInstance().openProjects) {
          val recentFilesModel = BackendRecentFilesModel.getInstance(project)

          val projectFilesToRename = renamedFiles.filter { file ->
            file.isValid && ProjectFileIndex.getInstance(project).isInContent(file)
          }
          if (projectFilesToRename.isNotEmpty()) {
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_EDITED, FileChangeKind.ADDED, projectFilesToRename)
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED, FileChangeKind.ADDED, projectFilesToRename)
            recentFilesModel.applyBackendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, FileChangeKind.ADDED, projectFilesToRename)
          }
        }
      }
    }
  }
}