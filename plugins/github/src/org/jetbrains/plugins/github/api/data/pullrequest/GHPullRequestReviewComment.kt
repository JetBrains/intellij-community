// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHComment
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import java.util.*

@GraphQLFragment("/graphql/fragment/pullRequestReviewComment.graphql")
data class GHPullRequestReviewComment(override val id: String,
                                      val databaseId: Long,
                                      val url: String,
                                      override val author: GHActor?,
                                      override val body: String,
                                      override val createdAt: Date,
                                      val state: GHPullRequestReviewCommentState,
                                      val commit: GHCommitHash?,
                                      val originalCommit: GHCommitHash?,
                                      val diffHunk: String,
                                      @JsonProperty("pullRequestReview") private val pullRequestReview: GHNode?,
                                      val viewerCanDelete: Boolean,
                                      val viewerCanUpdate: Boolean)
  : GHComment(id, author, body, createdAt) {
  val reviewId = pullRequestReview?.id
}