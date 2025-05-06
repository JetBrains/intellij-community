// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesApplicationCoroutineScopeProvider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
private class RecentFilesEditorTypingListener : AnActionListener {
  private val recentlyChangedFiles = MutableSharedFlow<FileAndProject>(extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val LOG = thisLogger()

  init {
    RecentFilesApplicationCoroutineScopeProvider.getInstance().coroutineScope.launch {
      recentlyChangedFiles.debounce(1.seconds)
        .collect { (file, project) ->
          LOG.debug("Adding file to the frontend recent files model after typing: ${file.name}")
          FrontendRecentFilesModel.getInstanceAsync(project).applyFrontendChanges(RecentFileKind.RECENTLY_EDITED, listOf(file), true)
        }
    }
  }

  override fun afterEditorTyping(c: Char, dataContext: DataContext) {
    val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isFile } ?: return
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    recentlyChangedFiles.tryEmit(FileAndProject(file, project))
  }

  private data class FileAndProject(val file: VirtualFile, val project: Project)
}