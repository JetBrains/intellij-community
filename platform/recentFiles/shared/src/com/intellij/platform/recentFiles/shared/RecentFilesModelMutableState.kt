// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class RecentFilesModelMutableState(project: Project) : RecentFilesMutableState<VirtualFile>(project) {
  fun getFilesByKind(filesKind: RecentFileKind): List<VirtualFile> {
    return chooseStateToWriteTo(filesKind).value.entries
  }
}
