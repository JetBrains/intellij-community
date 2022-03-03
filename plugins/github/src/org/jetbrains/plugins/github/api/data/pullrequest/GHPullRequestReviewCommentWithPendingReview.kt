// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHNode
import java.util.*

open class GHPullRequestReviewCommentWithPendingReview(id: String,
                                                       databaseId: Long,
                                                       url: String,
                                                       author: GHActor?,
                                                       body: String,
                                                       createdAt: Date,
                                                       state: GHPullRequestReviewCommentState,
                                                       path: String,
                                                       commit: GHCommitHash?,
                                                       position: Int?,
                                                       originalCommit: GHCommitHash?,
                                                       originalPosition: Int,
                                                       replyTo: GHNode?,
                                                       diffHunk: String,
                                                       @JsonProperty("pullRequestReview") val pullRequestReview: GHPullRequestPendingReview,
                                                       viewerCanDelete: Boolean,
                                                       viewerCanUpdate: Boolean)
  : GHPullRequestReviewComment(id, databaseId, url, author, body, createdAt, state, path, commit, position, originalCommit,
                               originalPosition, replyTo, diffHunk, pullRequestReview, viewerCanDelete, viewerCanUpdate) {
}
