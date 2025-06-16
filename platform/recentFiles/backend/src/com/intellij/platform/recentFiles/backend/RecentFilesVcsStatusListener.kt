// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.recentFiles.shared.FileChangeKind

internal class RecentFilesVcsStatusListener(private val project: Project) : FileStatusListener {
  override fun fileStatusChanged(virtualFile: VirtualFile) {
    val newStatus = FileStatusManager.getInstance(project).getStatus(virtualFile)
    thisLogger().trace { "Files to apply changes for: ${virtualFile.name} with new VCS status $newStatus" }
    BackendRecentFileEventsController.applyRelevantEventsToModel(listOf(virtualFile), FileChangeKind.UPDATED, project)
  }

  override fun fileStatusesChanged() {
    thisLogger().trace { "Update all existing files in model on VCS status change in project ${project.name}" }
    BackendRecentFileEventsController.updateAllExistingFilesInModel(project)
  }
}