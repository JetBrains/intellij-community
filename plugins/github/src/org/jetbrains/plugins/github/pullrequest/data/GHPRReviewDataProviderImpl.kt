// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProviderUtil.futureOfMutableOnEDT
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import java.util.concurrent.CompletableFuture

class GHPRReviewDataProviderImpl(private val reviewService: GHPRReviewService, private val pullRequestId: GHPRIdentifier)
  : GHPRReviewDataProvider {

  private val reviewThreadsRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadReviewThreads(it, pullRequestId)
  }

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
                          body: String,
                          replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return reviewService.addComment(progressIndicator, pullRequestId, body, replyToCommentId)
  }

  override fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<Unit> {
    return reviewService.deleteComment(progressIndicator, pullRequestId, commentId)
  }

  override fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment> {
    return reviewService.updateComment(progressIndicator, pullRequestId, commentId, newText)
  }

  override fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit) =
    reviewThreadsRequestValue.addDropEventListener(disposable, listener)
}