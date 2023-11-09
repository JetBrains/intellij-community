// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.diff.editor.DiffVirtualFile
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabMergeRequestDiffFile(override val connectionId: String,
                                 private val project: Project,
                                 private val glProject: GitLabProjectCoordinates,
                                 val mergeRequestIid: String)
  : DiffVirtualFile(GitLabBundle.message("merge.request.diff.file.name", mergeRequestIid)),
    VirtualFilePathWrapper,
    GitLabVirtualFile {

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun enforcePresentableName() = true

  override fun isValid(): Boolean = GitLabMergeRequestDiffService.isDiffFileValid(project, connectionId)

  override fun getPath(): String =
    (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject, mergeRequestIid, true)

  override fun getPresentablePath(): String = presentablePath(glProject, mergeRequestIid)

  override fun createProcessor(project: Project): DiffRequestProcessor =
    project.service<GitLabMergeRequestDiffService>().createDiffRequestProcessor(connectionId, mergeRequestIid)

  override fun getFileSystem(): VirtualFileSystem = GitLabVirtualFileSystem.getInstance()
  override fun getFileType(): FileType = FileTypes.UNKNOWN

  override fun getLength() = 0L
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestDiffFile) return false

    if (connectionId != other.connectionId) return false
    if (project != other.project) return false
    if (glProject != other.glProject) return false
    return mergeRequestIid == other.mergeRequestIid
  }

  override fun hashCode(): Int {
    var result = connectionId.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + glProject.hashCode()
    result = 31 * result + mergeRequestIid.hashCode()
    return result
  }

  override fun toString(): String =
    "GitLabMergeRequestDiffFile(connectionId='$connectionId', project=$project, glProject=$glProject, mergeRequestId=$mergeRequestIid)"
}


internal fun presentablePath(glProject: GitLabProjectCoordinates, mergeRequestIid: String): String =
  "$glProject/mergerequests/${mergeRequestIid}.diff"