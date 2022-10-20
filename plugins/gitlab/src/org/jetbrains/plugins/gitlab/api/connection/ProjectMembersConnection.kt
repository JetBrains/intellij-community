// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.connection

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabMemberDTO

class ProjectMembersConnection(
  pageInfo: GraphQLCursorPageInfoDTO,
  nodes: List<GitLabMemberDTO>
) : GraphQLConnectionDTO<GitLabMemberDTO>(pageInfo, nodes)