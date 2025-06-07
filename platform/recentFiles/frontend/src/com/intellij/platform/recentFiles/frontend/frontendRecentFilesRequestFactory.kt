// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import org.jetbrains.annotations.ApiStatus

private val LOG by lazy {
  fileLogger()
}

internal fun createFilesSearchRequestRequest(recentFileKind: RecentFileKind, project: Project): RecentFilesBackendRequest.FetchFiles {
  val filesFromFrontendEditorSelectionHistory = if (recentFileKind == RecentFileKind.RECENTLY_OPENED_UNPINNED) collectFilesFromFrontendEditorSelectionHistory(project) else emptyList()
  LOG.trace { "Frontend files from editor selection history: ${filesFromFrontendEditorSelectionHistory.joinToString { it.name }}" }
  return RecentFilesBackendRequest.FetchFiles(
    filesKind = recentFileKind,
    frontendEditorSelectionHistory = filesFromFrontendEditorSelectionHistory.map(VirtualFile::rpcId),
    projectId = project.projectId()
  )
}

internal fun createHideFilesRequest(recentFileKind: RecentFileKind, filesToHide: List<VirtualFile>, project: Project): RecentFilesBackendRequest.HideFiles {
  LOG.trace { "Collected files to hide: ${filesToHide.joinToString { it.name }}" }
  return RecentFilesBackendRequest.HideFiles(
    filesKind = recentFileKind,
    filesToHide = filesToHide.map { it.rpcId() },
    projectId = project.projectId()
  )
}

internal fun createFilesUpdateRequest(recentFileKind: RecentFileKind, frontendRecentFiles: List<VirtualFile>, forceAddToModel: Boolean, project: Project): RecentFilesBackendRequest.FetchMetadata {
  LOG.trace { "Frontend files to update: ${frontendRecentFiles.joinToString { it.name }}" }
  return RecentFilesBackendRequest.FetchMetadata(
    filesKind = recentFileKind,
    frontendRecentFiles = frontendRecentFiles.map { it.rpcId() },
    forceAddToModel = forceAddToModel,
    projectId = project.projectId()
  )
}