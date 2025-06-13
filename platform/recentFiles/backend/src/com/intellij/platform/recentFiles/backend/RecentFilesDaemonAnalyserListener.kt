// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.platform.recentFiles.shared.FileChangeKind

internal class RecentFilesDaemonAnalyserListener(private val project: Project) : DaemonListener {
  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val highlightedFiles = fileEditors.map { it.file }
    thisLogger().trace { "Files to apply changes for: ${highlightedFiles.joinToString { it.name }}" }
    BackendRecentFileEventsController.applyRelevantEventsToModel(highlightedFiles, FileChangeKind.UPDATED, project)
  }
}