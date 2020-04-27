// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import java.util.concurrent.CompletableFuture

interface GHPRReviewServiceAdapter {

  @CalledInAwt
  fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>>

  @CalledInAwt
  fun resetReviewThreads()

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
  fun deleteComment(progressIndicator: ProgressIndicator, commentId: String)
    : CompletableFuture<Unit>

  @CalledInAny
  fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment>

  @CalledInAwt
  fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit)

  companion object {
    @CalledInAny
    fun create(reviewService: GHPRReviewService, dataProvider: GHPRDataProvider): GHPRReviewServiceAdapter {
      return object : GHPRReviewServiceAdapter {

        override fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>> {
          return dataProvider.reviewThreadsRequest
        }

        override fun resetReviewThreads() = dataProvider.reloadReviewThreads()

        override fun canComment() = reviewService.canComment()

        override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String> {
          return reviewService.getCommentMarkdownBody(progressIndicator, commentId)
        }

        override fun addComment(progressIndicator: ProgressIndicator,
                                body: String,
                                commitSha: String,
                                fileName: String,
                                diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml> {
          return reviewService.addComment(progressIndicator, dataProvider.number, body, commitSha, fileName, diffLine)
        }

        override fun addComment(progressIndicator: ProgressIndicator,
                                body: String,
                                replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
          return reviewService.addComment(progressIndicator, dataProvider.number, body, replyToCommentId)
        }

        override fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<Unit> {
          return reviewService.deleteComment(progressIndicator, dataProvider.number, commentId)
        }

        override fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
          : CompletableFuture<GHPullRequestReviewComment> {
          return reviewService.updateComment(progressIndicator, dataProvider.number, commentId, newText)
        }

        override fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit) {
          dataProvider.addRequestsChangesListener(disposable, object : GHPRDataProvider.RequestsChangedListener {
            override fun reviewThreadsRequestChanged() {
              listener()
            }
          })
        }
      }
    }
  }
}