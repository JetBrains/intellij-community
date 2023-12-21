// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import java.util.*

@GraphQLFragment("/graphql/fragment/pullRequestReviewThread.graphql")
data class GHPullRequestReviewThread(override val id: String,
                                     val isResolved: Boolean,
                                     val isOutdated: Boolean,
                                     val path: String,
                                     @JsonProperty("diffSide") val side: Side,
                                     val line: Int?,
                                     val originalLine: Int?,
                                     @JsonProperty("startDiffSide") val startSide: Side?,
                                     val startLine: Int?,
                                     val originalStartLine: Int?,
                                     @JsonProperty("comments") private val commentsNodes: GraphQLNodesDTO<GHPullRequestReviewComment>,
                                     val viewerCanReply: Boolean,
                                     val viewerCanResolve: Boolean,
                                     val viewerCanUnresolve: Boolean)
  : GHNode(id) {
  @JsonIgnore
  val comments: List<GHPullRequestReviewComment> = commentsNodes.nodes
  private val root = commentsNodes.nodes.first()

  val state: GHPullRequestReviewCommentState = root.state
  val commit: GHCommitHash? = root.commit
  val originalCommit: GHCommitHash? = root.originalCommit
  val author: GHActor? = root.author
  val createdAt: Date = root.createdAt
  val diffHunk: String = root.diffHunk
  val reviewId: String? = root.reviewId
}