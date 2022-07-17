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
                                                       commit: GHCommitHash?,
                                                       originalCommit: GHCommitHash?,
                                                       replyTo: GHNode?,
                                                       diffHunk: String,
                                                       @JsonProperty("pullRequestReview") val pullRequestReview: GHPullRequestPendingReview,
                                                       viewerCanDelete: Boolean,
                                                       viewerCanUpdate: Boolean)
  : GHPullRequestReviewComment(id, databaseId, url, author, body, createdAt, state, commit, originalCommit,
                               replyTo, diffHunk, pullRequestReview, viewerCanDelete, viewerCanUpdate) {
}
