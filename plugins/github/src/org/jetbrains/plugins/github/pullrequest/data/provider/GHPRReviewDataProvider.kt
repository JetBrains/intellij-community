// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import java.util.concurrent.CompletableFuture

interface GHPRReviewDataProvider {

  val pendingReviewComment: MutableStateFlow<String>

  @RequiresEdt
  fun loadPendingReview(): CompletableFuture<GHPullRequestPendingReview?>

  @RequiresEdt
  fun resetPendingReview()

  @RequiresEdt
  fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>>

  @RequiresEdt
  fun resetReviewThreads()

  suspend fun createReview(event: GHPullRequestReviewEvent, body: String? = null): GHPullRequestPendingReview

  suspend fun submitReview(reviewId: String, event: GHPullRequestReviewEvent, body: String? = null)

  @RequiresEdt
  fun createReview(progressIndicator: ProgressIndicator,
                   event: GHPullRequestReviewEvent? = null, body: String? = null,
                   commitSha: String? = null,
                   threads: List<GHPullRequestDraftReviewThread>? = null)
    : CompletableFuture<GHPullRequestPendingReview>

  @RequiresEdt
  fun updateReviewBody(progressIndicator: ProgressIndicator, reviewId: String, newText: String): CompletableFuture<@NlsSafe String>

  suspend fun deleteReview(reviewId: String)

  @RequiresEdt
  fun canComment(): Boolean

  @RequiresEdt
  fun addComment(progressIndicator: ProgressIndicator,
                 reviewId: String,
                 body: String,
                 commitSha: String,
                 fileName: String,
                 side: Side,
                 line: Int)
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
  fun createThread(progressIndicator: ProgressIndicator,
                   reviewId: String,
                   body: String,
                   line: Int,
                   side: Side,
                   startLine: Int,
                   fileName: String)
    : CompletableFuture<GHPullRequestReviewThread>

  @RequiresEdt
  fun resolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @RequiresEdt
  fun unresolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @RequiresEdt
  fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit)

  @RequiresEdt
  fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit)
}

fun GHPRReviewDataProvider.createThreadsRequestsFlow(): Flow<CompletableFuture<List<GHPullRequestReviewThread>>> = callbackFlow {
  val disposable = Disposer.newDisposable()
  addReviewThreadsListener(disposable) {
    trySend(loadReviewThreads())
  }
  send(loadReviewThreads())
  awaitClose { Disposer.dispose(disposable) }
}