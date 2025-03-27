// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.recentFiles.shared.RecentFileKind

private class ChangedFilesVfsListener(private val project: Project) : IdeDocumentHistoryImpl.RecentPlacesListener {
  override fun recentPlaceAdded(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean) {
    updateRecentFilesModel(isChanged, true, changePlace)
  }

  override fun recentPlaceRemoved(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean) {
    updateRecentFilesModel(isChanged, false, changePlace)
  }

  private fun updateRecentFilesModel(isChanged: Boolean, isAdded: Boolean, changePlace: IdeDocumentHistoryImpl.PlaceInfo) {
    if (!isChanged) return

    val changedFile = changePlace.file.takeIf { it.isValid && it.isFile } ?: return
    BackendRecentFilesModel.getInstance(project).applyBackendChanges(RecentFileKind.RECENTLY_EDITED, changedFile, isAdded)
  }
}