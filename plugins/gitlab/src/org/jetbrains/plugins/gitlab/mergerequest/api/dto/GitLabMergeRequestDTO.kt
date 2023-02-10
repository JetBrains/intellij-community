// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiffRefs
import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeStatus
import java.util.*

@GraphQLFragment("/graphql/fragment/mergeRequest.graphql")
class GitLabMergeRequestDTO(
  val id: String,
  override val iid: String,
  val title: String,
  val description: String,
  val webUrl: String,
  val createdAt: Date,
  val targetBranch: String,
  val sourceBranch: String,
  val diffRefs: GitLabDiffRefs,
  val conflicts: Boolean,
  val mergeStatusEnum: GitLabMergeStatus,
  val state: GitLabMergeRequestState,
  val draft: Boolean,
  val author: GitLabUserDTO,
  val sourceProject: GitLabProjectDTO,
  approvedBy: UserCoreConnection,
  assignees: AssigneeConnection,
  reviewers: ReviewerConnection,
  commits: CommitConnection,
  val userPermissions: UserPermissions
) : GitLabMergeRequestId {
  val approvedBy: List<GitLabUserDTO> = approvedBy.nodes

  val assignees: List<GitLabUserDTO> = assignees.nodes
  
  val reviewers: List<GitLabUserDTO> = reviewers.nodes

  val commits: List<GitLabCommitDTO> = commits.nodes

  class UserCoreConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabUserDTO>)
    : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  class AssigneeConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabUserDTO>)
    : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  class ReviewerConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabUserDTO>)
    : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  class CommitConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabCommitDTO>)
    : GraphQLConnectionDTO<GitLabCommitDTO>(pageInfo, nodes)

  data class UserPermissions(
    val createNote: Boolean
  )
}