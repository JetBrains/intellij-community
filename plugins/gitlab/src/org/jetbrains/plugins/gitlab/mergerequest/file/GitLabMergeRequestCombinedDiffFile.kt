// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.diff.tools.combined.CombinedDiffModelBuilder
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.tools.combined.CombinedDiffVirtualFile
import com.intellij.ide.actions.SplitAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal class GitLabMergeRequestCombinedDiffFile(
  override val connectionId: String,
  private val project: Project,
  private val glProject: GitLabProjectCoordinates,
  private val mergeRequestIid: String,
) : CombinedDiffVirtualFile(mergeRequestIid, GitLabBundle.message("merge.request.diff.file.name", mergeRequestIid)),
    VirtualFilePathWrapper,
    GitLabVirtualFile,
    CombinedDiffModelBuilder {

  override fun createModel(id: String): CombinedDiffModelImpl =
    project.service<GitLabMergeRequestDiffService>().createGitLabCombinedDiffModel(connectionId, mergeRequestIid)

  init {
    putUserData(SplitAction.FORBID_TAB_SPLIT, true)
  }

  override fun getPresentablePath(): String = presentablePath(glProject, mergeRequestIid)

  override fun enforcePresentableName(): Boolean = true

  override fun getLength() = 0L
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun isValid(): Boolean = GitLabMergeRequestDiffService.isDiffFileValid(project, connectionId)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestCombinedDiffFile) return false

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
    "GitLabMergeRequestCombinedDiffFile(connectionId='$connectionId', project=$project, glProject=$glProject, mergeRequestId=$mergeRequestIid)"
}