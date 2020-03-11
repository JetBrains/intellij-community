// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentWithPendingReview
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRReviewServiceImpl(private val progressManager: ProgressManager,
                            private val messageBus: MessageBus,
                            private val securityService: GHPRSecurityService,
                            private val requestExecutor: GithubApiRequestExecutor,
                            private val repository: GHRepositoryCoordinates) : GHPRReviewService {

  override fun canComment() = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)

  override fun loadPendingReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.pendingReviews(repository.serverPath, pullRequestId.id)).nodes.singleOrNull()
    }

  override fun loadReviewThreads(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      SimpleGHGQLPagesLoader(requestExecutor, { p ->
        GHGQLRequests.PullRequest.reviewThreads(repository, pullRequestId.number, p)
      }).loadAll(it)
    }

  override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String> {
    return progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.getCommentBody(repository.serverPath, commentId))
    }
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier,
                          body: String,
                          replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(
        GithubApiRequests.Repos.PullRequests.Comments.createReply(repository, pullRequestId.number, replyToCommentId, body))
      messageBus.syncPublisher(GHPRDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequestId)
      comment
    }
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(
        GithubApiRequests.Repos.PullRequests.Comments.create(repository, pullRequestId.number, commitSha, fileName, diffLine, body))
      messageBus.syncPublisher(GHPRDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequestId)
      comment
    }
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier, reviewId: String?,
                          body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<GHPullRequestReviewCommentWithPendingReview> =
    progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(progressIndicator,
                                            GHGQLRequests.PullRequest.Review.addComment(repository.serverPath,
                                                                                        pullRequestId.id, reviewId,
                                                                                        body, commitSha, fileName,
                                                                                        diffLine))
      messageBus.syncPublisher(GHPRDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequestId)
      comment
    }

  override fun deleteComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String) =
    progressManager.submitIOTask(progressIndicator) {
      val review = requestExecutor.execute(GHGQLRequests.PullRequest.Review.deleteComment(repository.serverPath, commentId))
      messageBus.syncPublisher(GHPRDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequestId)
      review
    }

  override fun updateComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String, newText: String) =
    progressManager.submitIOTask(progressIndicator) {
      val comment = requestExecutor.execute(GHGQLRequests.PullRequest.Review.updateComment(repository.serverPath, commentId, newText))
      messageBus.syncPublisher(GHPRDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequestId)
      comment
    }
}