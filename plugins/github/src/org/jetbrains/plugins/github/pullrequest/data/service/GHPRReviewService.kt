// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import java.util.concurrent.CompletableFuture

interface GHPRReviewService {
  fun canComment(): Boolean

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator, pullRequest: Long, body: String, replyToCommentId: Long)
    : CompletableFuture<GithubPullRequestCommentWithHtml>

  @CalledInAny
  fun addComment(progressIndicator: ProgressIndicator,
                 pullRequest: Long,
                 body: String,
                 commitSha: String,
                 fileName: String,
                 diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml>
}
