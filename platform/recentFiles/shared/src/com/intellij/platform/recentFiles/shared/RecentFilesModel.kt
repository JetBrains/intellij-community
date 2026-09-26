// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private val LOG by lazy { fileLogger() }

/**
 * The list of the recent files of this process per [RecentFileKind].
 *
 * [RecentFileEventsModelSynchronizer] feeds it from [RecentFileEventsModel], and the event model reads it back to
 * decide which reported file belongs to the model.
 */
@Service(Service.Level.PROJECT)
internal class RecentFilesModel(private val project: Project) {
  private val modelState = RecentFilesModelMutableState(project)

  fun getFilesByKind(filesKind: RecentFileKind): List<VirtualFile> {
    return modelState.getFilesByKind(filesKind)
  }

  suspend fun subscribeToRecentFileEvents(targetFilesKind: RecentFileKind) {
    LOG.debug("Started collecting recent files updates for kind: $targetFilesKind")
    RecentFileEventsModel.getInstanceAsync(project)
      .getRecentFiles(targetFilesKind)
      .collect { update -> applyChangesToModel(update, targetFilesKind) }
  }

  private fun applyChangesToModel(event: LocalRecentFilesEvent, targetFilesKind: RecentFileKind) {
    when (event) {
      is LocalRecentFilesEvent.ItemsAdded -> modelState.addEvent(targetFilesKind, event.batch.map { it.virtualFile })
      is LocalRecentFilesEvent.ItemsUpdated -> modelState.updateEvent(targetFilesKind, event.batch.map { it.virtualFile }, event.putOnTop)
      is LocalRecentFilesEvent.ItemsRemoved -> modelState.removeEvent(targetFilesKind, event.batch)
      is LocalRecentFilesEvent.AllItemsRemoved -> modelState.removeAllEvent(targetFilesKind)
    }
  }

  companion object {
    suspend fun getInstanceAsync(project: Project): RecentFilesModel {
      return project.serviceAsync(RecentFilesModel::class.java)
    }

    fun getInstance(project: Project): RecentFilesModel {
      return project.service<RecentFilesModel>()
    }
  }
}
