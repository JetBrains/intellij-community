// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.0")
@GraphQLFragment("graphql/fragment/userDetailed.graphql")
class GitLabUserDetailedDTO(
  id: String,
  username: @NlsSafe String,
  name: @NlsSafe String,
  avatarUrl: String?,
  webUrl: String,
  @SinceGitLab("13.1") projectMemberships: ProjectMemberConnection,
  @SinceGitLab("13.1") groupMemberships: GroupMemberConnection
) : GitLabUserDTO(id, username, name, avatarUrl, webUrl) {
  val projectMemberships: List<GitLabProjectMemberDTO> = projectMemberships.nodes

  val groupMemberships: List<GitLabGroupMemberDTO> = groupMemberships.nodes

  class ProjectMemberConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabProjectMemberDTO>)
    : GraphQLConnectionDTO<GitLabProjectMemberDTO>(pageInfo, nodes)

  class GroupMemberConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabGroupMemberDTO>)
    : GraphQLConnectionDTO<GitLabGroupMemberDTO>(pageInfo, nodes)
}