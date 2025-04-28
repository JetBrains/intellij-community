// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ChangedIdeHistoryFileHistoryOrderListener(private val project: Project) : IdeDocumentHistoryImpl.RecentFileHistoryOrderListener {
  override fun recentFileUpdated(file: VirtualFile) {
    BackendRecentFileEventsController.applyRelevantEventsToModel(listOf(file), FileChangeKind.UPDATED_AND_PUT_ON_TOP, project)
  }
}