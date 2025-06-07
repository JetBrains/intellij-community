// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.util.CodeReviewFilesUtil
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection

interface GitLabMergeRequestsFilesController {
  @RequiresEdt
  fun openTimeline(mrIid: String, focus: Boolean)

  @RequiresEdt
  fun openDiff(mrIid: String, focus: Boolean)

  suspend fun closeAllFiles()
}

class GitLabMergeRequestsFilesControllerImpl(
  private val project: Project,
  private val connection: GitLabProjectConnection
) : GitLabMergeRequestsFilesController {

  override fun openTimeline(mrIid: String, focus: Boolean) {
    val fs = GitLabVirtualFileSystem.getInstance()
    val path = fs.getPath(connection.id, project, connection.repo.repository, mrIid)
    val file = fs.refreshAndFindFileByPath(path) ?: return
    FileEditorManager.getInstance(project).openFile(file, focus)
  }

  override fun openDiff(mrIid: String, focus: Boolean) {
    val fs = GitLabVirtualFileSystem.getInstance()
    val path = fs.getPath(connection.id, project, connection.repo.repository, mrIid, true)
    val file = fs.refreshAndFindFileByPath(path) ?: return
    DiffEditorTabFilesManager.getInstance(project).showDiffFile(file, focus)
  }

  override suspend fun closeAllFiles() {
    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext
      val fileManager = project.serviceAsync<FileEditorManager>()
      edtWriteAction {
        val files = fileManager.openFiles.filter { file ->
          file is GitLabVirtualFile && connection.id == file.connectionId
        }
        CodeReviewFilesUtil.closeFilesSafely(fileManager, files)
      }
    }
  }
}
