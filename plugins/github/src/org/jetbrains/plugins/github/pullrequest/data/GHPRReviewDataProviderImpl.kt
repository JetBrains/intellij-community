// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProviderUtil.futureOfMutableOnEDT
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture

class GHPRReviewDataProviderImpl(private val reviewService: GHPRReviewService, private val pullRequestId: GHPRIdentifier)
  : GHPRReviewDataProvider {

  private val pendingReviewRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadPendingReview(it, pullRequestId)
  }

  private val reviewThreadsRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadReviewThreads(it, pullRequestId)
  }

  override fun loadPendingReview() = futureOfMutableOnEDT { pendingReviewRequestValue.value }

  override fun resetPendingReview() = pendingReviewRequestValue.drop()

  override fun loadReviewThreads() = futureOfMutableOnEDT { reviewThreadsRequestValue.value }

  override fun resetReviewThreads() = reviewThreadsRequestValue.drop()

  override fun canComment() = reviewService.canComment()

  override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String> {
    return reviewService.getCommentMarkdownBody(progressIndicator, commentId)
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return reviewService.addComment(progressIndicator, pullRequestId, body, commitSha, fileName, diffLine)
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          reviewId: String?,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<out GHPullRequestReviewComment> {
    val future = reviewService.addComment(progressIndicator, pullRequestId, reviewId, body, commitSha, fileName,
                                          diffLine)
    if (reviewId == null) {
      pendingReviewRequestValue.overrideProcess(future.handleOnEdt { result, error ->
        if (error != null) {
          ApplicationManager.getApplication().invokeLater {
            pendingReviewRequestValue.drop()
          }
          throw ProcessCanceledException()
        }
        result.pullRequestReview
      })
    }
    return future
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String,
                          replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return reviewService.addComment(progressIndicator, pullRequestId, body, replyToCommentId)
  }

  override fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<out Any> {
    val future = reviewService.deleteComment(progressIndicator, pullRequestId, commentId)

    pendingReviewRequestValue.overrideProcess(future.handleOnEdt { result, error ->
      if (error != null || (result.state != GHPullRequestReviewState.PENDING || result.comments.totalCount != 0)) {
        ApplicationManager.getApplication().invokeLater {
          pendingReviewRequestValue.drop()
        }
        throw ProcessCanceledException()
      }
      null
    })
    return future
  }

  override fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment> {
    return reviewService.updateComment(progressIndicator, pullRequestId, commentId, newText)
  }

  override fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit) =
    reviewThreadsRequestValue.addDropEventListener(disposable, listener)

  override fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit) =
    pendingReviewRequestValue.addDropEventListener(disposable, listener)
}