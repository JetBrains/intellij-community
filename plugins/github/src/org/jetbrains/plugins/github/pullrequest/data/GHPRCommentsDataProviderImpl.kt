// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService
import java.util.concurrent.CompletableFuture

class GHPRCommentsDataProviderImpl(private val commentService: GHPRCommentService,
                                   private val pullRequestId: GHPRIdentifier) : GHPRCommentsDataProvider {

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String): CompletableFuture<GithubIssueCommentWithHtml> =
    commentService.addComment(progressIndicator, pullRequestId, body)
}