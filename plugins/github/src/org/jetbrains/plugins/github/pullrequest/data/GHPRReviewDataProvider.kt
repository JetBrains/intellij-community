// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import java.util.concurrent.CompletableFuture

interface GHPRReviewDataProvider {
  @CalledInAwt
  fun loadPendingReview(): CompletableFuture<GHPullRequestPendingReview?>

  @CalledInAwt
  fun resetPendingReview()

  @CalledInAwt
  fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>>

  @CalledInAwt
  fun resetReviewThreads()

  @CalledInAny
  fun submitReview(progressIndicator: ProgressIndicator, reviewId: String?, event: GHPullRequestReviewEvent, body: String? = null)
    : CompletableFuture<out Any?>

  @CalledInAny
  fun deleteReview(progressIndicator: ProgressIndicator, reviewId: String): CompletableFuture<out Any?>

  @CalledInAny
  fun canComment(): Boolean

  @CalledInAny
  fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, body: String, replyToCommentId: Long)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, reviewId: String?, body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<out GHPullRequestReviewComment>

  @CalledInAny
  fun deleteComment(progressIndicator: ProgressIndicator, commentId: String)
    : CompletableFuture<out Any>

  @CalledInAny
  fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment>

  @CalledInAny
  fun resolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @CalledInAny
  fun unresolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread>

  @CalledInAwt
  fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit)

  @CalledInAwt
  fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit)
}