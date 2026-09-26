// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ChangedIdeHistoryFileHistoryOrderListener(private val project: Project) : IdeDocumentHistoryImpl.RecentFileHistoryOrderListener {
  override fun recentFileUpdated(file: VirtualFile) {
    thisLogger().trace { "Files to apply changes for: ${file.name}" }
    RecentFileEventsController.filesAdded(project, listOf(file))
  }
}
