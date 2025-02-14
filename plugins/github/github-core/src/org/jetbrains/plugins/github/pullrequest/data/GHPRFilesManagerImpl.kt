// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.util.CodeReviewFilesUtil
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFileSystem

internal class GHPRFilesManagerImpl(
  private val project: Project,
  private val repository: GHRepositoryCoordinates,
) : GHPRFilesManager {

  // current time should be enough to distinguish the manager between launches
  override val id: String = System.currentTimeMillis().toString()

  private val fs by lazy { GHPRVirtualFileSystem.getInstance() }

  override fun createAndOpenTimelineFile(prId: GHPRIdentifier, requestFocus: Boolean) {
    val path = fs.getPath(id, project, repository, prId, false)
    fs.refreshAndFindFileByPath(path)?.let {
      FileEditorManager.getInstance(project).openFile(it, requestFocus)
      GHPRStatisticsCollector.logTimelineOpened(project)
    }
  }

  override fun createAndOpenDiffFile(prId: GHPRIdentifier?, requestFocus: Boolean) {
    val path = fs.getPath(id, project, repository, prId, true)
    fs.refreshAndFindFileByPath(path).also {
      if (prId != null) {
        GHPRStatisticsCollector.logDiffOpened(project)
      }
      it?.let {
        DiffEditorTabFilesManager.getInstance(project).showDiffFile(it, requestFocus)
      }
    }
  }

  override fun updateTimelineFilePresentation(prId: GHPRIdentifier) {
    val path = fs.getPath(id, project, repository, prId, false)
    fs.refreshAndFindFileByPath(path)?.let {
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(it)
    }
  }

  override suspend fun closeNewPrFile() {
    withContext(Dispatchers.EDT) {
      val path = fs.getPath(id, project, repository, null, true)
      val file = fs.refreshAndFindFileByPath(path) ?: return@withContext
      val fileManager = project.serviceAsync<FileEditorManager>()
      edtWriteAction {
        CodeReviewFilesUtil.closeFilesSafely(fileManager, listOf(file))
      }
    }
  }

  override suspend fun closeAllFiles() {
    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext
      val fileManager = project.serviceAsync<FileEditorManager>()
      writeAction {
        val files = fileManager.openFiles.filter { file ->
          file is GHPRVirtualFile && id == file.fileManagerId
        }
        CodeReviewFilesUtil.closeFilesSafely(fileManager, files)
      }
    }
  }
}
