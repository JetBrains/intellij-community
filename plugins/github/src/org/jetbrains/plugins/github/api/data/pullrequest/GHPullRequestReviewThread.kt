// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.GHNodes

class GHPullRequestReviewThread(id: String,
                                val isResolved: Boolean,
                                @JsonProperty("comments") comments: GHNodes<GHPullRequestReviewComment>)
  : GHNode(id) {
  val comments = comments.nodes
  private val root = comments.nodes.first()

  val state = root.state

  val path = root.path
  val commit = root.commit
  val position = root.position
  val originalCommit = root.originalCommit
  val originalPosition = root.originalPosition
  val createdAt = root.createdAt
  val diffHunk = root.diffHunk
  val isOutdated = root.position == null

  val reviewId = root.reviewId
}
