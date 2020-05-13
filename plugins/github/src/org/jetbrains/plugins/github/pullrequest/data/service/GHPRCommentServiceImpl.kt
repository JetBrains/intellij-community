// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRCommentServiceImpl(private val progressManager: ProgressManager,
                             private val requestExecutor: GithubApiRequestExecutor,
                             private val repository: GHRepositoryCoordinates) : GHPRCommentService {

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier,
                          body: String): CompletableFuture<GithubIssueCommentWithHtml> {
    return progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(
        GithubApiRequests.Repos.Issues.Comments.create(repository, pullRequestId.number, body))
      comment
    }.logError(LOG, "Error occurred while adding PR comment")
  }

  companion object {
    private val LOG = logger<GHPRCommentService>()
  }
}