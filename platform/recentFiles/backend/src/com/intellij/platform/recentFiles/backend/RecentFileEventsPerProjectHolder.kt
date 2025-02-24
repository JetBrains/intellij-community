// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.codeInsight.daemon.HighlightingPassesCache
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

@Service(Service.Level.PROJECT)
internal class RecentFileEventsPerProjectHolder {
  private val recentFiles = MutableSharedFlow<RecentFilesEvent>(
    extraBufferCapacity = 10,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  fun getRecentFiles(): Flow<RecentFilesEvent> {
    LOG.debug("Switcher get recent files")
    return recentFiles
  }

  suspend fun emitRecentFiles(searchRequest: RecentFilesBackendRequest.NewSearchWithParameters) {
    LOG.debug("Switcher emit recent files: $searchRequest")

    recentFiles.emit(RecentFilesEvent.AllItemsRemoved())
    val freshRecentFiles = collectRecentFiles(searchRequest)
    if (freshRecentFiles != null) {
      recentFiles.emit(freshRecentFiles)
    }
    recentFiles.emit(RecentFilesEvent.EndOfUpdates())
  }

  suspend fun hideAlreadyShownFiles(hideFilesRequest: RecentFilesBackendRequest.HideFiles) {
    LOG.debug("Switcher hide file: $hideFilesRequest")
    val project = hideFilesRequest.projectId.findProjectOrNull() ?: return
    val virtualFiles = hideFilesRequest.filesToHide.mapNotNull { it.virtualFileId.virtualFile() }
    for (file in virtualFiles) {
      EditorHistoryManager.getInstance(project).removeFile(file)
    }
    recentFiles.emit(RecentFilesEvent.ItemsRemoved(hideFilesRequest.filesToHide))
    recentFiles.emit(RecentFilesEvent.EndOfUpdates())
  }

  fun scheduleRehighlightUnopenedFiles(projectId: ProjectId) {
    val project = projectId.findProjectOrNull() ?: return
    LOG.debug("Switcher rehighlight files in project: $projectId")
    HighlightingPassesCache.getInstance(project).schedule(getNotOpenedRecentFiles(project))
  }

  private fun getNotOpenedRecentFiles(project: Project): List<VirtualFile> {
    val recentFiles = EditorHistoryManager.getInstance(project).fileList
    val openFiles = FileEditorManager.getInstance(project).openFiles
    return recentFiles.subtract(openFiles.toSet()).toList()
  }

  private suspend fun collectRecentFiles(filter: RecentFilesBackendRequest.NewSearchWithParameters): RecentFilesEvent? {
    LOG.debug("Switcher started fetching recent files")
    val project = filter.projectId.findProjectOrNull() ?: return null

    val collectedFiles = readAction {
      getFilesToShow(project, filter.onlyEdited, filter.pinned, filter.frontendEditorSelectionHistory)
    }
    LOG.debug("Switcher collected ${collectedFiles.size} recent files")
    LOG.trace { "Switcher collected recent files list: ${collectedFiles.joinToString(prefix = "\n", separator = "\n") { it.mainText }}" }

    return RecentFilesEvent.ItemsAdded(collectedFiles)
  }

  companion object {
    fun getInstance(project: Project): RecentFileEventsPerProjectHolder {
      return project.service<RecentFileEventsPerProjectHolder>()
    }
  }
}

private val LOG by lazy { fileLogger() }