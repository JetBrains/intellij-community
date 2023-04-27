// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.GitlabIcons
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Icon

internal class GitLabMergeRequestTimelineFile(connectionId: String,
                                              project: Project,
                                              glProject: GitLabProjectCoordinates,
                                              val mergeRequestId: GitLabMergeRequestId)
  : GitLabProjectVirtualFile(connectionId, project, glProject) {

  override fun getName() = "!${mergeRequestId.iid}"
  override fun getPresentableName(): @Nls String = GitLabBundle.message("merge.request.timeline.file.name", mergeRequestId.iid)

  override fun getPath(): String = (fileSystem as GitLabVirtualFileSystem).getPath(connectionId, project, glProject,
                                                                                   GitLabMergeRequestId.Simple(mergeRequestId))

  override fun getPresentablePath() = findDetails()?.webUrl ?: "$glProject/mergerequests/${mergeRequestId.iid}"

  override fun getFileType(): FileType = GitLabTimelineFileType.instance

  private fun findDetails(): GitLabMergeRequestDetails? =
    findContext()?.projectData?.mergeRequests?.findCachedDetails(mergeRequestId)

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

private class GitLabTimelineFileType private constructor() : FileType {
  override fun getName(): String = "GitLabTimeline"

  override fun getDescription(): @Nls String = GitLabBundle.message("merge.request.timeline.filetype.description")

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = GitlabIcons.GitLabLogo

  override fun isBinary(): Boolean = true

  override fun isReadOnly(): Boolean = true

  companion object {
    val instance = GitLabTimelineFileType()
  }
}