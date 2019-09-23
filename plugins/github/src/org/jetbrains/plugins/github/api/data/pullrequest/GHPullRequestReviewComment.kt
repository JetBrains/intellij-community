// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHComment
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import java.util.*

class GHPullRequestReviewComment(id: String,
                                 val url: String,
                                 author: GHActor?,
                                 bodyHTML: String,
                                 createdAt: Date,
                                 val path: String,
                                 val commit: GHCommitHash,
                                 val position: Int?,
                                 val originalCommit: GHCommitHash?,
                                 val originalPosition: Int,
                                 val replyTo: GHNode?,
                                 val diffHunk: String,
                                 @JsonProperty("pullRequestReview") pullRequestReview: GHNode)
  : GHComment(id, author, bodyHTML, createdAt) {
  val reviewId = pullRequestReview.id
}
