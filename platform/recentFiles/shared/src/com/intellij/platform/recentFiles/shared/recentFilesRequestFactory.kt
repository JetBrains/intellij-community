// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun createFilesUpdateRequest(recentFileKind: RecentFileKind, frontendRecentFiles: List<VirtualFile>, project: Project): RecentFilesBackendRequest.FetchMetadata {
  return RecentFilesBackendRequest.FetchMetadata(
    filesKind = recentFileKind,
    frontendRecentFiles = frontendRecentFiles.map { it.rpcId() },
    projectId = project.projectId()
  )
}