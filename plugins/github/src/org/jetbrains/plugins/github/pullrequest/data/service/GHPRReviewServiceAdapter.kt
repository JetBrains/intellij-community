// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import java.util.concurrent.CompletableFuture

interface GHPRReviewServiceAdapter {

  @CalledInAwt
  fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>>

  @CalledInAwt
  fun resetReviewThreads()

  @CalledInAny
  fun canComment(): Boolean

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, body: String, replyToCommentId: Long)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAwt
  fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit)

  companion object {
    @CalledInAny
    fun create(reviewService: GHPRReviewService, dataProvider: GithubPullRequestDataProvider): GHPRReviewServiceAdapter {
      return object : GHPRReviewServiceAdapter {

        override fun loadReviewThreads(): CompletableFuture<List<GHPullRequestReviewThread>> {
          return dataProvider.reviewThreadsRequest
        }

        override fun resetReviewThreads() = dataProvider.reloadReviewThreads()

        override fun canComment() = reviewService.canComment()

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

        override fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit) {
          dataProvider.addRequestsChangesListener(disposable, object : GithubPullRequestDataProvider.RequestsChangedListener {
            override fun reviewThreadsRequestChanged() {
              listener()
            }
          })
        }
      }
    }
  }
}