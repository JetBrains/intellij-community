// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import java.util.concurrent.CompletableFuture

interface GHPRReviewDataProvider {

  val submitReviewCommentDocument: Document

  @RequiresEdt
  fun loadPendingReview(): CompletableFuture<GHPullRequestPendingReview?>

  @RequiresEdt
  fun resetPendingReview()

  @RequiresEdt
  fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>>

  @RequiresEdt
  fun resetReviewThreads()

  @RequiresEdt
  fun submitReview(progressIndicator: ProgressIndicator, reviewId: String, event: GHPullRequestReviewEvent, body: String? = null)
    : CompletableFuture<out Any?>

  @RequiresEdt
  fun createReview(progressIndicator: ProgressIndicator,
                   event: GHPullRequestReviewEvent? = null, body: String? = null,
                   commitSha: String? = null, comments: List<GHPullRequestDraftReviewComment>? = null)
    : CompletableFuture<GHPullRequestPendingReview>

  @RequiresEdt
  fun getReviewMarkdownBody(progressIndicator: ProgressIndicator, reviewId: String): CompletableFuture<String>

  @RequiresEdt
  fun updateReviewBody(progressIndicator: ProgressIndicator, reviewId: String, newText: String): CompletableFuture<String>

  @RequiresEdt
  fun deleteReview(progressIndicator: ProgressIndicator, reviewId: String): CompletableFuture<out Any?>

  @RequiresEdt
  fun canComment(): Boolean

  @RequiresEdt
  fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String>

  @RequiresEdt
  fun addComment(progressIndicator: ProgressIndicator, reviewId: String, body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<out GHPullRequestReviewComment>

  @RequiresEdt
  fun addComment(progressIndicator: ProgressIndicator, replyToCommentId: String, body: String)
    : CompletableFuture<out GHPullRequestReviewComment>

  @RequiresEdt
  fun deleteComment(progressIndicator: ProgressIndicator, commentId: String)
    : CompletableFuture<out Any>

  @RequiresEdt
  fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment>

  @RequiresEdt
  fun resolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @RequiresEdt
  fun unresolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @RequiresEdt
  fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit)
}