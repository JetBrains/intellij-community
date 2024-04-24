// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.diff.util.Side
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRReviewService {
  fun canComment(): Boolean

  suspend fun loadPendingReview(pullRequestId: GHPRIdentifier): GHPullRequestPendingReviewDTO?

  suspend fun loadReviewThreads(pullRequestId: GHPRIdentifier): List<GHPullRequestReviewThread>

  suspend fun createReview(pullRequestId: GHPRIdentifier,
                           event: GHPullRequestReviewEvent? = null,
                           body: String? = null,
                           commitSha: String? = null,
                           threads: List<GHPullRequestDraftReviewThread>? = null): GHPullRequestPendingReviewDTO

  suspend fun submitReview(pullRequestId: GHPRIdentifier, reviewId: String, event: GHPullRequestReviewEvent, body: String?)

  suspend fun updateReviewBody(reviewId: String, newText: String): GHPullRequestReview

  suspend fun deleteReview(pullRequestId: GHPRIdentifier, reviewId: String)

  suspend fun addComment(pullRequestId: GHPRIdentifier, reviewId: String, replyToCommentId: String, body: String): GHPullRequestReviewComment

  suspend fun addComment(reviewId: String, body: String, commitSha: String, fileName: String, diffLine: Int): GHPullRequestReviewComment

  suspend fun addThread(reviewId: String, body: String, line: Int, side: Side, startLine: Int, fileName: String): GHPullRequestReviewThread

  suspend fun deleteComment(pullRequestId: GHPRIdentifier, commentId: String): GHPullRequestPendingReviewDTO

  suspend fun updateComment(pullRequestId: GHPRIdentifier, commentId: String, newText: String): GHPullRequestReviewComment

  suspend fun resolveThread(pullRequestId: GHPRIdentifier, id: String): GHPullRequestReviewThread

  suspend fun unresolveThread(pullRequestId: GHPRIdentifier, id: String): GHPullRequestReviewThread
}
