// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesMutableState

internal class BackendRecentFilesMutableState(project: Project) : RecentFilesMutableState<VirtualFile>(project) {
  override fun isAllowedInModel(targetFilesKind: RecentFileKind, model: VirtualFile): Boolean {
    return isAllowedInRecentFilesModel(project, model)
  }

  fun getFilesByKind(filesKind: RecentFileKind): List<VirtualFile> {
    return chooseStateToWriteTo(filesKind).value.entries
  }
}

internal fun isAllowedInRecentFilesModel(project: Project, file: VirtualFile): Boolean {
  return file.isValid && (file !is IdeDocumentHistoryImpl.OptionallyIncluded || file.isIncludedInDocumentHistory(project))
}