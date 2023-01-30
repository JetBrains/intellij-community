// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

internal object GitLabTimelinesController {
  fun openTimeline(
    project: Project,
    repo: GitLabProjectCoordinates,
    mr: GitLabMergeRequestId,
    focus: Boolean = false
  ) {
    // TODO: introduce virtual files manager like SpaceVirtualFilesManager
    val id = System.nanoTime().toString()
    val fs = GitLabVirtualFileSystem.getInstance()
    val path = fs.getPath(id, project, repo, mr)
    val file = fs.refreshAndFindFileByPath(path) ?: return
    FileEditorManager.getInstance(project).openFile(file, focus)
  }

  // TODO: all timelines should be closed on connection change. Who should subscribe on it?
  fun closeAllTimelines(project: Project) {
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
