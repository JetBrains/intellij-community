// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener

internal class RecentFilesProblemsListener(private val project: Project) : ProblemListener {
  override fun problemsAppeared(file: VirtualFile) {
    BackendRecentFilesModel.getInstance(project).applyBackendChangesToAllFileKinds(FileChangeKind.UPDATED, listOf(file))
  }

  override fun problemsChanged(file: VirtualFile) {
    BackendRecentFilesModel.getInstance(project).applyBackendChangesToAllFileKinds(FileChangeKind.UPDATED, listOf(file))
  }

  override fun problemsDisappeared(file: VirtualFile) {
    BackendRecentFilesModel.getInstance(project).applyBackendChangesToAllFileKinds(FileChangeKind.UPDATED, listOf(file))
  }
}