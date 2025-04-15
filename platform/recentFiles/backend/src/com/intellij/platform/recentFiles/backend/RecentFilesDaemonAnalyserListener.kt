// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project

internal class RecentFilesDaemonAnalyserListener(private val project: Project) : DaemonListener {
  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val highlightedFiles = fileEditors.map { it.file }
    thisLogger().debug("Updating recent files model with new highlighting info for: ${highlightedFiles.joinToString { it.name }}")
    BackendRecentFilesModel.getInstance(project).applyBackendChangesToAllFileKinds(FileChangeKind.UPDATED, highlightedFiles)
  }
}