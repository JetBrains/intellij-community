// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import java.util.concurrent.CompletableFuture

interface GHPRReviewServiceAdapter {
  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, body: String, replyToCommentId: Long)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  companion object {
    @CalledInAny
    fun create(reviewService: GHPRReviewService, pullRequest: Long): GHPRReviewServiceAdapter {
      return object : GHPRReviewServiceAdapter {
        override fun addComment(progressIndicator: ProgressIndicator,
                                body: String,
                                replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
          return reviewService.addComment(progressIndicator, pullRequest, body, replyToCommentId)
        }
      }
    }
  }
}