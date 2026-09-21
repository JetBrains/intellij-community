// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * Serves [FileSwitcherApi] from the recent files model of this process.
 *
 * The shared module registers it as the application service behind [FileSwitcherApi]. A light session resolves that
 * service as the local fallback of [FileSwitcherApi.getInstance], and the backend module serves the same instance over
 * RPC to a connected frontend.
 */
internal class FileSwitcherApiImpl : FileSwitcherApi {

  override suspend fun getRecentFileEvents(fileKind: RecentFileKind, projectId: ProjectId): Flow<RecentFilesEvent> {
    LOG.debug("Switcher fetching recent files for projectId: $projectId and fileKind: $fileKind")
    val recentFileEventsModel = getRecentFileEventsModel(projectId) ?: return emptyFlow()
    return recentFileEventsModel.getRecentFiles(fileKind).map { event -> event.toRpcModel() }
  }

  override suspend fun updateRecentFilesBackendState(request: RecentFilesBackendRequest): Boolean {
    val recentFileEventsModel = getRecentFileEventsModel(request.projectId) ?: return false
    when (request) {
      is RecentFilesBackendRequest.FetchMetadata -> recentFileEventsModel.emitRecentFilesMetadata(request)
      is RecentFilesBackendRequest.FetchFiles -> recentFileEventsModel.emitRecentFiles(request)
      is RecentFilesBackendRequest.HideFiles -> recentFileEventsModel.hideAlreadyShownFiles(request)
      is RecentFilesBackendRequest.ScheduleRehighlighting -> recentFileEventsModel.scheduleRehighlightUnopenedFiles()
    }
    return true
  }

  private suspend fun getRecentFileEventsModel(projectId: ProjectId): RecentFileEventsModel? {
    val project = projectId.findProjectOrNull()
    if (project == null) {
      LOG.debug("Switcher unable to resolve project from projectId, recent files request will be ignored for projectId: $projectId")
      return null
    }
    LOG.debug("Switcher found recent files holder for projectId: $projectId")
    return RecentFileEventsModel.getInstanceAsync(project)
  }
}

private val LOG by lazy { fileLogger() }
