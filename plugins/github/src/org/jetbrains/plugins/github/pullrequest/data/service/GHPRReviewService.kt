// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentWithPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
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
                   event: GHPullRequestReviewEvent,
                   body: String?): CompletableFuture<out Any?>

  @CalledInAny
  fun submitReview(progressIndicator: ProgressIndicator,
                   pullRequestId: GHPRIdentifier,
                   reviewId: String,
                   event: GHPullRequestReviewEvent,
                   body: String?): CompletableFuture<out Any?>

  @CalledInAny
  fun deleteReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, reviewId: String): CompletableFuture<out Any?>

  @CalledInAny
  fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, body: String, replyToCommentId: Long)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator,
                 pullRequestId: GHPRIdentifier,
                 body: String,
                 commitSha: String,
                 fileName: String,
                 diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator,
                 pullRequestId: GHPRIdentifier, reviewId: String?,
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
