// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile

internal class RecentFilesVcsStatusListener(private val project: Project) : FileStatusListener {
  override fun fileStatusChanged(virtualFile: VirtualFile) {
    val newStatus = FileStatusManager.getInstance(project).getStatus(virtualFile)
    thisLogger().debug("Updating recent files model with new VCS status $newStatus for: ${virtualFile.name}")
    BackendRecentFilesModel.getInstance(project).applyBackendChangesToAllFileKinds(FileChangeKind.UPDATED, listOf(virtualFile))
  }

  override fun fileStatusesChanged() {
    BackendRecentFilesModel.getInstance(project).emitUncertainChange()
  }
}