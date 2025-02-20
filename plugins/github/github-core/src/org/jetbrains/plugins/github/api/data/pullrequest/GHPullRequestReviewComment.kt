// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.*
import java.util.*

@GraphQLFragment("/graphql/fragment/pullRequestReviewComment.graphql")
data class GHPullRequestReviewComment(
  override val id: String,
  val url: String,
  override val author: GHActor?,
  override val body: String,
  override val createdAt: Date,
  override val reactions: GHReactable.ReactionConnection,
  val state: GHPullRequestReviewCommentState,
  val commit: GHCommitHash?,
  val originalCommit: GHCommitHash?,
  val diffHunk: String,
  @JsonProperty("pullRequestReview") private val pullRequestReview: GHNode?,
  val viewerCanDelete: Boolean,
  val viewerCanUpdate: Boolean,
  val viewerCanReact: Boolean,
) : GHComment(id, author, body, createdAt, reactions) {
  @JsonIgnore
  val reviewId = pullRequestReview?.id
}