// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import java.util.concurrent.CompletableFuture

interface GHPRReviewDataProvider {

  val submitReviewCommentDocument: Document

  @CalledInAwt
  fun loadPendingReview(): CompletableFuture<GHPullRequestPendingReview?>

  @CalledInAwt
  fun resetPendingReview()

  @CalledInAwt
  fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>>

  @CalledInAwt
  fun resetReviewThreads()

  @CalledInAwt
  fun submitReview(progressIndicator: ProgressIndicator, reviewId: String, event: GHPullRequestReviewEvent, body: String? = null)
    : CompletableFuture<out Any?>

  @CalledInAwt
  fun createReview(progressIndicator: ProgressIndicator,
                   event: GHPullRequestReviewEvent? = null, body: String? = null,
                   commitSha: String? = null, comments: List<GHPullRequestDraftReviewComment>? = null)
    : CompletableFuture<GHPullRequestPendingReview>

  @CalledInAwt
  fun deleteReview(progressIndicator: ProgressIndicator, reviewId: String): CompletableFuture<out Any?>

  @CalledInAwt
  fun canComment(): Boolean

  @CalledInAwt
  fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String>

  @CalledInAwt
  fun addComment(progressIndicator: ProgressIndicator, reviewId: String, body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<out GHPullRequestReviewComment>

  @CalledInAwt
  fun addComment(progressIndicator: ProgressIndicator, replyToCommentId: String, body: String)
    : CompletableFuture<out GHPullRequestReviewComment>

  @CalledInAwt
  fun deleteComment(progressIndicator: ProgressIndicator, commentId: String)
    : CompletableFuture<out Any>

  @CalledInAwt
  fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment>

  @CalledInAwt
  fun resolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @CalledInAwt
  fun unresolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @CalledInAwt
  fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit)
}