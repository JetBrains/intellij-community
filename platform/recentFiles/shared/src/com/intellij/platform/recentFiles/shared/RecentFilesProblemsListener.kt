// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener

internal class RecentFilesProblemsListener(private val project: Project) : ProblemListener {
  override fun problemsAppeared(file: VirtualFile) {
    updateFile(file)
  }

  override fun problemsChanged(file: VirtualFile) {
    updateFile(file)
  }

  override fun problemsDisappeared(file: VirtualFile) {
    updateFile(file)
  }

  private fun updateFile(file: VirtualFile) {
    thisLogger().trace { "Files to apply changes for: ${file.name}" }
    RecentFileEventsController.presentationChanged(project, listOf(file))
  }
}
