// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

class GitLabFilesController(
  private val project: Project,
  private val repo: GitLabProjectCoordinates
) {

  private val id = System.nanoTime().toString()

  fun openTimeline(mr: GitLabMergeRequestId, focus: Boolean = false) {
    val fs = GitLabVirtualFileSystem.getInstance()
    val path = fs.getPath(id, project, repo, mr)
    val file = fs.refreshAndFindFileByPath(path) ?: return
    FileEditorManager.getInstance(project).openFile(file, focus)
  }

  fun closeAllFiles() {
    val fileManager = FileEditorManager.getInstance(project)
    runWriteAction {
      // cache?
      fileManager.openFiles.forEach { file ->
        if (GitLabVirtualFileSystem.MANAGED_FILE_CLASSES.any { it.isInstance(file) }) {
          fileManager.closeFile(file)
        }
      }
    }
  }
}
