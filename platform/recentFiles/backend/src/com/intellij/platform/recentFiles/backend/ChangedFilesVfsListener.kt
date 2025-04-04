// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.isFile

internal class ChangedFilesVfsListener(private val project: Project) : IdeDocumentHistoryImpl.RecentPlacesListener {
  override fun recentPlaceAdded(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean) {}

  override fun recentPlaceAdded(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean, groupId: Any?) {
    updateRecentFilesModel(isChanged, FileChangeKind.ADDED, changePlace)
  }

  override fun recentPlaceRemoved(changePlace: IdeDocumentHistoryImpl.PlaceInfo, isChanged: Boolean) {
    updateRecentFilesModel(isChanged, FileChangeKind.REMOVED, changePlace)
  }

  private fun updateRecentFilesModel(isChanged: Boolean, changeKind: FileChangeKind, changePlace: IdeDocumentHistoryImpl.PlaceInfo) {
    if (!isChanged || ApplicationManager.getApplication().isUnitTestMode) return
    val changedFile = changePlace.file.takeIf { it.isValid && it.isFile } ?: return
    val recentFilesModel = BackendRecentFilesModel.getInstance(project)

    recentFilesModel.applyBackendChangesToAllFileKinds(changeKind, listOf(changedFile))
  }
}