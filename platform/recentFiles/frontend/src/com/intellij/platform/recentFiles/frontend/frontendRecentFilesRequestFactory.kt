// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest

internal fun createFilesSearchRequestRequest(onlyEdited: Boolean, pinned: Boolean, project: Project): RecentFilesBackendRequest.NewSearchWithParameters {
  val filesFromFrontendEditorSelectionHistory = if (!pinned) collectFilesFromFrontendEditorSelectionHistory(project) else emptyList()
  return RecentFilesBackendRequest.NewSearchWithParameters(
    onlyEdited = onlyEdited,
    pinned = pinned,
    frontendEditorSelectionHistory = filesFromFrontendEditorSelectionHistory,
    projectId = project.projectId()
  )
}