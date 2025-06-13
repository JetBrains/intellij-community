// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.FileChangeKind
import com.intellij.problems.ProblemListener

internal class RecentFilesProblemsListener(private val project: Project) : ProblemListener {
  override fun problemsAppeared(file: VirtualFile) {
    thisLogger().trace { "Files to apply changes for: ${file.name}" }
    BackendRecentFileEventsController.applyRelevantEventsToModel(listOf(file), FileChangeKind.UPDATED, project)
  }

  override fun problemsChanged(file: VirtualFile) {
    thisLogger().trace { "Files to apply changes for: ${file.name}" }
    BackendRecentFileEventsController.applyRelevantEventsToModel(listOf(file), FileChangeKind.UPDATED, project)
  }

  override fun problemsDisappeared(file: VirtualFile) {
    thisLogger().trace { "Files to apply changes for: ${file.name}" }
    BackendRecentFileEventsController.applyRelevantEventsToModel(listOf(file), FileChangeKind.UPDATED, project)
  }
}