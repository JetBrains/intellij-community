// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId

internal class GitLabMergeRequestTimelineFile(connectionId: String,
                                              project: Project,
                                              glProject: GitLabProjectCoordinates,
                                              val mergeRequestId: GitLabMergeRequestId)
  : GitLabProjectVirtualFile(connectionId, project, glProject) {

  override fun getName() = "#${mergeRequestId.iid}"
  override fun getPresentableName() = findDetails()?.let { "${it.title} ${it.iid}" } ?: name

  override fun getPath(): String = (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject,
                                                                                   GitLabMergeRequestId.Simple(mergeRequestId))

  override fun getPresentablePath() = findDetails()?.webUrl ?: "$glProject/mergerequests/${mergeRequestId.iid}"

  private fun findDetails(): GitLabMergeRequestDetails? =
    findConnection()?.projectData?.mergeRequests?.findCachedDetails(mergeRequestId)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestTimelineFile) return false
    if (!super.equals(other)) return false

    return mergeRequestId == other.mergeRequestId
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + mergeRequestId.hashCode()
    return result
  }
}
