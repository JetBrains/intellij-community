// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestDTO
import java.util.*

data class GitLabMergeRequestFullDetails(
  override val iid: String,
  override val title: @NlsSafe String,
  override val createdAt: Date,
  override val author: GitLabUserDTO,
  override val mergeStatus: GitLabMergeStatus,
  override val isMergeable: Boolean,
  override val state: GitLabMergeRequestState,
  override val draft: Boolean,
  override val assignees: List<GitLabUserDTO>,
  override val reviewers: List<GitLabUserDTO>,
  override val webUrl: @NlsSafe String,
  val detailedLabels: List<GitLabLabelDTO>,
  val targetProject: GitLabProjectDTO,
  val sourceProject: GitLabProjectDTO,
  val description: String,
  val approvedBy: List<GitLabUserDTO>,
  val targetBranch: String,
  val sourceBranch: String,
  val conflicts: Boolean,
  val commits: List<GitLabCommitDTO>,
  val diffRefs: GitLabDiffRefs,
  val headPipeline: GitLabPipelineDTO?,
  val userPermissions: GitLabMergeRequestPermissionsDTO
) : GitLabMergeRequestDetails(iid, title, createdAt, author, mergeStatus, isMergeable, state, draft, assignees, reviewers, webUrl,
                              detailedLabels.map { it.title }) {

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
      conflicts = dto.conflicts,
      commits = dto.commits,
      diffRefs = dto.diffRefs,
      headPipeline = dto.headPipeline,
      userPermissions = dto.userPermissions,
      detailedLabels = dto.labels
    )
  }
}