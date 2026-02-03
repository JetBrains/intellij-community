// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO

class GitLabProjectsDTO(pageInfo: GraphQLCursorPageInfoDTO,
                        nodes: List<GitLabProjectDTO>)
  : GraphQLConnectionDTO<GitLabProjectDTO>(pageInfo, nodes)