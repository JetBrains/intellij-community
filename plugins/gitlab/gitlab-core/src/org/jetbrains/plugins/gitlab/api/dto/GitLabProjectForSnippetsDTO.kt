// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.SinceGitLab

/**
 * Contains only the fields needed for the create-snippet dialog to display projects.
 * This is minimalized because some users report having hundreds of projects.
 */
@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/projectForSnippets.graphql")
data class GitLabProjectForSnippetsDTO(
  val name: @Nls String,
  val fullPath: @NlsSafe String,
  val userPermissions: ProjectUserPermissions,
) {
  val ownerPath: @NlsSafe String = fullPath.split("/").dropLast(1).joinToString("/")

  /**
   * Corresponds to what GL calls ProjectPermissions. These are the permissions a *user* has while accessing a GL *project*.
   */
  data class ProjectUserPermissions(
    @SinceGitLab("12.6") val createSnippet: Boolean
  )
}

class GitLabProjectsForSnippetsDTO(
  pageInfo: GraphQLCursorPageInfoDTO,
  nodes: List<GitLabProjectForSnippetsDTO>,
) : GraphQLConnectionDTO<GitLabProjectForSnippetsDTO>(pageInfo, nodes)