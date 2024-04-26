// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.GHRefUpdateRule
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest.BaseRef

@GraphQLFragment("/graphql/fragment/pullRequestMergeability.graphql")
data class GHPullRequestMergeabilityData(
  val mergeable: GHPullRequestMergeableState,
  val canBeRebased: Boolean,
  val mergeStateStatus: GHPullRequestMergeStateStatus,
  private val baseRef: BaseRef?,
  val commits: GHPullRequestCommitWithCheckStatusesConnection
) {

  @JsonIgnore
  val baseRefUpdateRule: GHRefUpdateRule? = baseRef?.refUpdateRule

  class GHPullRequestCommitWithCheckStatusesConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GHPullRequestCommitWithCheckStatuses>
  ) : GraphQLConnectionDTO<GHPullRequestCommitWithCheckStatuses>(pageInfo, nodes)
}