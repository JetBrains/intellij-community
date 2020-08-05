// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.*
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import java.util.concurrent.CompletableFuture

interface GHPRReviewService {

  fun canComment(): Boolean

  @CalledInAny
  fun loadPendingReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier)
    : CompletableFuture<GHPullRequestPendingReview?>

  @CalledInAny
  fun loadReviewThreads(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier)
    : CompletableFuture<List<GHPullRequestReviewThread>>

  @CalledInAny
  fun createReview(progressIndicator: ProgressIndicator,
                   pullRequestId: GHPRIdentifier,
                   event: GHPullRequestReviewEvent? = null,
                   body: String? = null,
                   commitSha: String? = null,
                   comments: List<GHPullRequestDraftReviewComment>? = null): CompletableFuture<GHPullRequestPendingReview>

  @CalledInAny
  fun submitReview(progressIndicator: ProgressIndicator,
                   pullRequestId: GHPRIdentifier,
                   reviewId: String,
                   event: GHPullRequestReviewEvent,
                   body: String?): CompletableFuture<out Any?>

  @CalledInAny
  fun updateReviewBody(progressIndicator: ProgressIndicator, reviewId: String, newText: String): CompletableFuture<GHPullRequestReview>

  @CalledInAny
  fun deleteReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, reviewId: String): CompletableFuture<out Any?>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator,
                 pullRequestId: GHPRIdentifier,
                 reviewId: String,
                 replyToCommentId: String,
                 body: String)
    : CompletableFuture<GHPullRequestReviewCommentWithPendingReview>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, reviewId: String,
                 body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<GHPullRequestReviewCommentWithPendingReview>

  @CalledInAny
  fun deleteComment(progressIndicator: ProgressIndicator,
                    pullRequestId: GHPRIdentifier,
                    commentId: String): CompletableFuture<GHPullRequestPendingReview>

  @CalledInAny
  fun updateComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment>

  @CalledInAny
  fun resolveThread(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, id: String)
    : CompletableFuture<GHPullRequestReviewThread>

  @CalledInAny
  fun unresolveThread(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, id: String)
    : CompletableFuture<GHPullRequestReviewThread>
}
