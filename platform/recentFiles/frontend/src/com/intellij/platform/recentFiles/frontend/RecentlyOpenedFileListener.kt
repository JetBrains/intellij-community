// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel
import com.intellij.platform.recentFiles.shared.RecentFileKind

private class RecentlySelectedEditorListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstance(source.project)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, listOf(file), true)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), true)
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstance(source.project)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), false)
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val file = event.newFile ?: return
    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstance(event.manager.project)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, listOf(file), true)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), true)
  }
}