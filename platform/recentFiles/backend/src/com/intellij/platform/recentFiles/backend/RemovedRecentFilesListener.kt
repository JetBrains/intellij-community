// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

internal class RemovedRecentFilesListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null

    val removedFiles = collectRemovedFiles(events)
    val movedFiles = collectMovedFiles(events)
    val renamedFiles = collectRenamedFiles(events)

    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        for (project in ProjectManager.getInstance().openProjects) {
          val recentFilesModel = BackendRecentFilesModel.getInstance(project)

          val projectFilesToRemove = filterProjectFiles(removedFiles, project)
          if (projectFilesToRemove.isNotEmpty()) {
            recentFilesModel.applyBackendChangesToAllFileKinds(FileChangeKind.REMOVED, projectFilesToRemove)
          }

          val projectFilesToUpdate = filterProjectFiles(movedFiles, project)
          if (projectFilesToUpdate.isNotEmpty()) {
            recentFilesModel.applyBackendChangesToAllFileKinds(FileChangeKind.UPDATED, projectFilesToRemove)
          }

          val projectFilesToRename = renamedFiles.filter { file ->
            file.isValid && ProjectFileIndex.getInstance(project).isInContent(file)
          }
          if (projectFilesToRename.isNotEmpty()) {
            recentFilesModel.applyBackendChangesToAllFileKinds(FileChangeKind.REMOVED, projectFilesToRemove)
          }
        }
      }

      override fun afterVfsChange() {
        for (project in ProjectManager.getInstance().openProjects) {
          val recentFilesModel = BackendRecentFilesModel.getInstance(project)

          val projectFilesToRename = filterProjectFiles(renamedFiles, project)
          if (projectFilesToRename.isNotEmpty()) {
            recentFilesModel.applyBackendChangesToAllFileKinds(FileChangeKind.ADDED, projectFilesToRename)
          }
        }
      }
    }
  }

  private fun filterProjectFiles(
    files: List<VirtualFile>,
    project: Project,
  ): List<VirtualFile> {
    return files.filter { file ->
      file.isFile && file.isValid && ProjectFileIndex.getInstance(project).isInContent(file)
    }
  }

  private fun collectRenamedFiles(events: List<VFileEvent>): List<VirtualFile> {
    return events.filterIsInstance<VFilePropertyChangeEvent>()
      .filter { it.propertyName == "name" }
      .map { it.file }
  }

  private fun collectMovedFiles(events: List<VFileEvent>): List<VirtualFile> {
    return events.filterIsInstance<VFileMoveEvent>()
      .map { it.file }
  }

  private fun collectRemovedFiles(events: List<VFileEvent>): List<VirtualFile> {
    return events.filterIsInstance<VFileDeleteEvent>()
      .map { it.file }
  }
}