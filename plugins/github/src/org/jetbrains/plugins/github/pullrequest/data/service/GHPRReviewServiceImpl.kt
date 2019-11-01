// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRReviewServiceImpl(private val progressManager: ProgressManager,
                            private val messageBus: MessageBus,
                            private val securityService: GithubPullRequestsSecurityService,
                            private val requestExecutor: GithubApiRequestExecutor,
                            private val repository: GHRepositoryCoordinates) : GHPRReviewService {
  override fun canComment() = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE)

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequest: Long,
                          body: String,
                          replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(
        GithubApiRequests.Repos.PullRequests.Comments.createReply(repository, pullRequest, replyToCommentId, body))
      messageBus.syncPublisher(GHPullRequestsDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequest)
      comment
    }
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequest: Long,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(
        GithubApiRequests.Repos.PullRequests.Comments.create(repository, pullRequest, commitSha, fileName, diffLine, body))
      messageBus.syncPublisher(GHPullRequestsDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequest)
      comment
    }
  }
}