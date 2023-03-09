// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.*

@GraphQLFragment("/graphql/fragment/pullRequestInfoShort.graphql")
open class GHPullRequestShort(id: String,
                              val url: String,
                              override val number: Long,
                              @NlsSafe val title: String,
                              val state: GHPullRequestState,
                              val isDraft: Boolean,
                              val author: GHActor?,
                              val createdAt: Date,
                              @JsonProperty("assignees") assignees: GraphQLNodesDTO<GHUser>,
                              @JsonProperty("labels") labels: GraphQLNodesDTO<GHLabel>,
                              @JsonProperty("reviewRequests") reviewRequests: GraphQLNodesDTO<GHPullRequestReviewRequest>,
                              @JsonProperty("reviewThreads") reviewThreads: GraphQLNodesDTO<ReviewThreadDetails>,
                              val mergeable: GHPullRequestMergeableState,
                              val viewerCanUpdate: Boolean,
                              val viewerDidAuthor: Boolean) : GHNode(id), GHPRIdentifier {

  @JsonIgnore
  val assignees = assignees.nodes

  @JsonIgnore
  val labels = labels.nodes

  @JsonIgnore
  val reviewRequests = reviewRequests.nodes

  @JsonIgnore
  val unresolvedReviewThreadsCount = reviewThreads.nodes.count { !it.isResolved && !it.isOutdated }

  override fun toString(): String = "#$number $title"

  class ReviewThreadDetails(val isResolved: Boolean, val isOutdated: Boolean)
}