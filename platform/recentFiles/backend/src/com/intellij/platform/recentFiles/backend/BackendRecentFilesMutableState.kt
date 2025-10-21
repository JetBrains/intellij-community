// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesMutableState

internal class BackendRecentFilesMutableState(project: Project) : RecentFilesMutableState<VirtualFile>(project) {
  override fun checkValidity(model: VirtualFile): Boolean {
    return model.isValid
  }

  fun getFilesByKind(filesKind: RecentFileKind): List<VirtualFile> {
    return chooseStateToWriteTo(filesKind).value.entries
  }
}