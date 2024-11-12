// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.SinceGitLab

/**
 * Contains only the fields needed for clone dialog to display projects.
 * This is minimalized because some users report having hundreds of projects.
 */
@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/projectForClone.graphql")
data class GitLabProjectForCloneDTO(
  val nameWithNamespace: @Nls String,
  val fullPath: @NlsSafe String,
  val httpUrlToRepo: @NlsSafe String?,
)

class GitLabProjectsForCloneDTO(
  pageInfo: GraphQLCursorPageInfoDTO,
  nodes: List<GitLabProjectForCloneDTO>,
) : GraphQLConnectionDTO<GitLabProjectForCloneDTO>(pageInfo, nodes)

