// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeStatus
import java.util.*

@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/mergeRequest.graphql")
@GraphQLFragment("/graphql/fragment/community/mergeRequest.graphql")
class GitLabMergeRequestDTO(
  val id: String,
  val iid: String,
  val title: String,
  val description: String?,
  val webUrl: String,
  val createdAt: Date,
  val targetBranch: String,
  val sourceBranch: String,
  @SinceGitLab("12.1") val diffRefs: GitLabDiffRefs?,
  @SinceGitLab("13.9", editions = [GitLabEdition.Enterprise]) val approvalsRequired: Int?,
  @SinceGitLab("13.4") val conflicts: Boolean,
  val headPipeline: GitLabPipelineDTO?,
  @SinceGitLab("14.0") val mergeStatusEnum: GitLabMergeStatus?,
  @SinceGitLab("13.7") val mergeable: Boolean,
  val state: GitLabMergeRequestState,
  @SinceGitLab("13.12") val draft: Boolean,
  @SinceGitLab("13.1") val author: GitLabUserDTO,
  val targetProject: GitLabProjectDTO,
  val sourceProject: GitLabProjectDTO?, // Is null when the source project is a private fork or is unavailable
  val userPermissions: GitLabMergeRequestPermissionsDTO,
  val shouldRemoveSourceBranch: Boolean?,
  val shouldBeRebased: Boolean,
  val rebaseInProgress: Boolean,
  @SinceGitLab("13.5") approvedBy: UserCoreConnection,
  @SinceGitLab("12.4") assignees: AssigneeConnection,
  @SinceGitLab("13.8") reviewers: ReviewerConnection,
  @SinceGitLab("14.7") commits: CommitConnection?,
  @SinceGitLab("12.4") labels: LabelConnection
) {
  val approvedBy: List<GitLabUserDTO> = approvedBy.nodes

  val assignees: List<GitLabUserDTO> = assignees.nodes

  val reviewers: List<GitLabReviewerDTO> = reviewers.nodes

  @SinceGitLab("14.7")
  val commits: List<GitLabCommitDTO>? = commits?.nodes

  val labels: List<GitLabLabelDTO> = labels.nodes

  @SinceGitLab("13.5")
  class UserCoreConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GitLabUserDTO>
  ) : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  @SinceGitLab("12.4")
  class AssigneeConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GitLabUserDTO>
  ) : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  @SinceGitLab("13.8")
  class ReviewerConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GitLabReviewerDTO>
  ) : GraphQLConnectionDTO<GitLabReviewerDTO>(pageInfo, nodes)

  @SinceGitLab("14.7")
  class CommitConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GitLabCommitDTO>
  ) : GraphQLConnectionDTO<GitLabCommitDTO>(pageInfo, nodes)

  @SinceGitLab("12.4")
  class LabelConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GitLabLabelDTO>
  ) : GraphQLConnectionDTO<GitLabLabelDTO>(pageInfo, nodes)
}