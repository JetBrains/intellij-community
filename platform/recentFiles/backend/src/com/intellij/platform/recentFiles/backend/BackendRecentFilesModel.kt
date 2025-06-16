// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.RecentFileKind

private val LOG by lazy { fileLogger() }

@Service(Service.Level.PROJECT)
internal class BackendRecentFilesModel(private val project: Project) {
  private val modelState = BackendRecentFilesMutableState(project)

  fun getFilesByKind(filesKind: RecentFileKind): List<VirtualFile> {
    return modelState.getFilesByKind(filesKind)
  }

  suspend fun subscribeToBackendRecentFilesUpdates(targetFilesKind: RecentFileKind) {
    LOG.debug("Started collecting recent files updates for kind: $targetFilesKind")
    BackendRecentFileEventsModel.getInstanceAsync(project)
      .getRecentFiles(targetFilesKind)
      .collect { update -> modelState.applyChangesToModel(update, targetFilesKind) }
  }

  companion object {
    suspend fun getInstanceAsync(project: Project): BackendRecentFilesModel {
      return project.serviceAsync(BackendRecentFilesModel::class.java)
    }

    fun getInstance(project: Project): BackendRecentFilesModel {
      return project.service<BackendRecentFilesModel>()
    }
  }
}