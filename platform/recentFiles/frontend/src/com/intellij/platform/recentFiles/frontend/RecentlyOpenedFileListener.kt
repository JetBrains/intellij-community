// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel
import com.intellij.platform.recentFiles.shared.FileChangeKind
import com.intellij.platform.recentFiles.shared.RecentFileKind

private class RecentlySelectedEditorListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    thisLogger().trace { "Add opened frontend file to model: ${file.name}" }
    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstance(source.project)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, listOf(file), FileChangeKind.ADDED)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), FileChangeKind.ADDED)
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    thisLogger().trace { "Maybe remove closed frontend file from model: ${file.name}" }
    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstance(source.project)
    if (source.getEditors(file).isEmpty()) {
      thisLogger().trace { "Do remove closed frontend file from model: ${file.name}" }
      frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), FileChangeKind.REMOVED)
    }
   }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val file = event.newFile ?: return
    thisLogger().trace { "Update frontend file in model: ${file.name}" }
    val frontendRecentFilesModel = FrontendRecentFilesModel.getInstance(event.manager.project)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED, listOf(file), FileChangeKind.UPDATED_AND_PUT_ON_TOP)
    frontendRecentFilesModel.applyFrontendChanges(RecentFileKind.RECENTLY_OPENED_UNPINNED, listOf(file), FileChangeKind.UPDATED_AND_PUT_ON_TOP)
  }
}