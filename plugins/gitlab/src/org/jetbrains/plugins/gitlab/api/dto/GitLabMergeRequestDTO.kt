// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestState

@GraphQLFragment("/graphql/fragment/mergeRequest.graphql")
class GitLabMergeRequestDTO(
  val id: String,
  override val iid: String,
  val title: String,
  val description: String,
  val webUrl: String,
  val targetBranch: String,
  val sourceBranch: String,
  val conflicts: Boolean,
  val state: GitLabMergeRequestState,
  val author: GitLabUserDTO,
  approvedBy: UserCoreConnection,
  reviewers: ReviewerConnection,
  commits: CommitConnection,
  val userPermissions: UserPermissions
) : GitLabMergeRequestId {
  val approvedBy: List<GitLabUserDTO> = approvedBy.nodes

  val reviewers: List<GitLabUserDTO> = reviewers.nodes

  val commits: List<GitLabCommitDTO> = commits.nodes

  class UserCoreConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabUserDTO>)
    : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  class ReviewerConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabUserDTO>)
    : GraphQLConnectionDTO<GitLabUserDTO>(pageInfo, nodes)

  class CommitConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabCommitDTO>)
    : GraphQLConnectionDTO<GitLabCommitDTO>(pageInfo, nodes)

  data class UserPermissions(
    val createNote: Boolean
  )
}