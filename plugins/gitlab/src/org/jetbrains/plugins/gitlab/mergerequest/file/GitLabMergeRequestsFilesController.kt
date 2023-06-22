// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

interface GitLabMergeRequestsFilesController {
  @RequiresEdt
  fun openTimeline(mr: GitLabMergeRequestId, focus: Boolean)

  @RequiresEdt
  fun openDiff(mr: GitLabMergeRequestId, focus: Boolean)

  suspend fun closeAllFiles()
}

class GitLabMergeRequestsFilesControllerImpl(
  private val project: Project,
  private val connection: GitLabProjectConnection
) : GitLabMergeRequestsFilesController {

  override fun openTimeline(mr: GitLabMergeRequestId, focus: Boolean) {
    val fs = GitLabVirtualFileSystem.getInstance()
    val path = fs.getPath(connection.id, project, connection.repo.repository, mr)
    val file = fs.refreshAndFindFileByPath(path) ?: return
    FileEditorManager.getInstance(project).openFile(file, focus)
  }

  override fun openDiff(mr: GitLabMergeRequestId, focus: Boolean) {
    val fs = GitLabVirtualFileSystem.getInstance()
    val path = fs.getPath(connection.id, project, connection.repo.repository, mr, true)
    val file = fs.refreshAndFindFileByPath(path) ?: return
    VcsEditorTabFilesManager.getInstance().openFile(project, file, focus)
  }

  override suspend fun closeAllFiles() {
    withContext(Dispatchers.EDT) {
      if (project.isDisposed) return@withContext
      val fileManager = FileEditorManager.getInstance(project)
      writeAction {
        // cache?
        fileManager.openFiles.forEach { file ->
          if (file is GitLabVirtualFile && connection.id == file.connectionId) {
            fileManager.closeFile(file)
          }
        }
      }
    }
  }
}
