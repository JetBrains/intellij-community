// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import com.intellij.platform.recentFiles.shared.RecentFilesEvent
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private class FilesSwitcherBackendApi : FileSwitcherApi {

  override suspend fun getRecentFileEvents(fileKind: RecentFileKind, projectId: ProjectId): Flow<RecentFilesEvent> {
    LOG.debug("Switcher fetching recent files for projectId: $projectId and fileKind: $fileKind")
    return getBackendRecentFilesModel(projectId)?.getRecentFiles(fileKind) ?: emptyFlow()
  }

  override suspend fun updateRecentFilesBackendState(request: RecentFilesBackendRequest): Boolean {
    val recentFilesModel = getBackendRecentFilesModel(request.projectId) ?: return false
    when (request) {
      is RecentFilesBackendRequest.FetchMetadata -> recentFilesModel.emitRecentFilesMetadata(request)
      is RecentFilesBackendRequest.FetchFiles -> recentFilesModel.emitRecentFiles(request)
      is RecentFilesBackendRequest.HideFiles -> recentFilesModel.hideAlreadyShownFiles(request)
      is RecentFilesBackendRequest.ScheduleRehighlighting -> recentFilesModel.scheduleRehighlightUnopenedFiles()
    }
    return true
  }

  private suspend fun getBackendRecentFilesModel(projectId: ProjectId): BackendRecentFilesModel? {
    val project = projectId.findProjectOrNull()
    if (project == null) {
      LOG.debug("Switcher unable to resolve project from projectId, recent files request will be ignored for projectId: $projectId")
      return null
    }
    LOG.debug("Switcher found recent files holder for projectId: $projectId")
    return BackendRecentFilesModel.getInstanceAsync(project)
  }
}

private class RecentFilesBackendApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<FileSwitcherApi>()) {
      FilesSwitcherBackendApi()
    }
  }
}

private val LOG by lazy { fileLogger() }