// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab

// not a data class bc change in cursor does not constitute a change in data
@SinceGitLab("12.0")
@GraphQLFragment("/graphql/fragment/pipeline.graphql")
class GitLabPipelineDTO(
  @SinceGitLab("13.6") jobs: CiJobConnection?
) {
  val jobs: List<GitLabCiJobDTO>? = jobs?.nodes?.distinctBy { it.name }

  class CiJobConnection(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<GitLabCiJobDTO>)
    : GraphQLConnectionDTO<GitLabCiJobDTO>(pageInfo, nodes)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabPipelineDTO) return false

    return jobs == other.jobs
  }

  override fun hashCode(): Int {
    return jobs.hashCode()
  }
}