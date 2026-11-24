// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabNoteLinks.OPEN_FILE_LINK_PREFIX
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabNoteLinks.OPEN_MR_LINK_PREFIX
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.nio.file.Path

/**
 * Opens a file of the repository in an editor, and a merge request in the tool window.
 */
internal class GitLabBackendNoteLinkHandler(private val project: Project) : GitLabNoteLinkHandler {
  @RequiresEdt
  override fun openGitLabLink(link: String) {
    if (link.startsWith(OPEN_FILE_LINK_PREFIX)) {
      val path = link.removePrefix(OPEN_FILE_LINK_PREFIX)
      val file = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path)) ?: return
      if (!file.exists()) return
      val descriptor = OpenFileDescriptor(project, file)
      FileEditorManager.getInstance(project).requestOpenTextEditor(descriptor, FileEditorOpenRequest.fromDescriptor(descriptor).withRequestFocus(true))
    }
    else if (link.startsWith(OPEN_MR_LINK_PREFIX)) {
      val mrIid = link.removePrefix(OPEN_MR_LINK_PREFIX)
      val projectVm = project.service<GitLabProjectViewModel>().connectedProjectVm.value ?: return
      projectVm.openMergeRequestDetails(mrIid, GitLabStatistics.ToolWindowOpenTabActionPlace.TIMELINE_LINK)
      projectVm.openMergeRequestTimeline(mrIid, false)
    }
  }
}
