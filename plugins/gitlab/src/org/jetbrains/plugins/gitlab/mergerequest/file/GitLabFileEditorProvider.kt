// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager

internal class GitLabFileEditorProvider : FileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is GitLabProjectVirtualFile && project.service<GitLabProjectConnectionManager>().connectionState.value != null
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    file as GitLabProjectVirtualFile
    val connection = project.service<GitLabProjectConnectionManager>().connectionState.value
                     ?: error("Not connected to GitLab repository")

    if (file !is GitLabMergeRequestTimelineFile) error("Unsupported file type")

    return GitLabMergeRequestTimelineFileEditor(project, connection, file)
  }

  override fun getEditorTypeId(): String = "GitLab"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
