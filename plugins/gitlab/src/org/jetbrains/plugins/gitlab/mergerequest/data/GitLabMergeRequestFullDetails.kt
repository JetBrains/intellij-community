// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import java.util.*

data class GitLabMergeRequestFullDetails(
  override val iid: String,
  val title: @NlsSafe String,
  val createdAt: Date,
  val author: GitLabUserDTO,
  val mergeStatus: GitLabMergeStatus,
  val isMergeable: Boolean,
  val state: GitLabMergeRequestState,
  val draft: Boolean,
  val assignees: List<GitLabUserDTO>,
  val reviewers: List<GitLabReviewerDTO>,
  val webUrl: @NlsSafe String,
  val detailedLabels: List<GitLabLabelDTO>,
  val targetProject: GitLabProjectDTO,
  val sourceProject: GitLabProjectDTO?,
  val description: String,
  val approvedBy: List<GitLabUserDTO>,
  val targetBranch: String,
  val sourceBranch: String,
  val isApproved: Boolean,
  val conflicts: Boolean,
  val commits: List<GitLabCommitDTO>,
  val diffRefs: GitLabDiffRefs,
  val headPipeline: GitLabPipelineDTO?,
  val userPermissions: GitLabMergeRequestPermissionsDTO,
  val shouldBeRebased: Boolean,
  val rebaseInProgress: Boolean
) : GitLabMergeRequestId {

  companion object {
    fun fromGraphQL(dto: GitLabMergeRequestDTO) = GitLabMergeRequestFullDetails(
      iid = dto.iid,
      title = dto.title,
      createdAt = dto.createdAt,
      author = dto.author,
      mergeStatus = dto.mergeStatusEnum,
      isMergeable = dto.mergeable,
      state = dto.state,
      draft = dto.draft,
      assignees = dto.assignees,
      reviewers = dto.reviewers,
      webUrl = dto.webUrl,
      targetProject = dto.targetProject,
      sourceProject = dto.sourceProject,
      description = dto.description,
      approvedBy = dto.approvedBy,
      targetBranch = dto.targetBranch,
      sourceBranch = dto.sourceBranch,
      isApproved = dto.approved ?: true,
      conflicts = dto.conflicts,
      commits = dto.commits,
      diffRefs = dto.diffRefs,
      headPipeline = dto.headPipeline,
      userPermissions = dto.userPermissions,
      detailedLabels = dto.labels,
      shouldBeRebased = dto.shouldBeRebased,
      rebaseInProgress = dto.rebaseInProgress
    )
  }
}

val GitLabMergeRequestFullDetails.reviewState: ReviewRequestState
  get() =
    if (draft) {
      ReviewRequestState.DRAFT
    }
    else when (state) {
      GitLabMergeRequestState.CLOSED -> ReviewRequestState.CLOSED
      GitLabMergeRequestState.MERGED -> ReviewRequestState.MERGED
      GitLabMergeRequestState.OPENED -> ReviewRequestState.OPENED
      else -> ReviewRequestState.OPENED // to avoid null state
    }