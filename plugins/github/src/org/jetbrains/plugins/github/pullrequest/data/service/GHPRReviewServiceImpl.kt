// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentWithPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRReviewServiceImpl(private val progressManager: ProgressManager,
                            private val securityService: GHPRSecurityService,
                            private val requestExecutor: GithubApiRequestExecutor,
                            private val repository: GHRepositoryCoordinates) : GHPRReviewService {

  override fun canComment() = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)

  override fun loadPendingReview(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.pendingReviews(repository.serverPath, pullRequestId.id)).nodes.singleOrNull()
    }.logError(LOG, "Error occurred while loading pending review")

  override fun loadReviewThreads(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      SimpleGHGQLPagesLoader(requestExecutor, { p ->
        GHGQLRequests.PullRequest.reviewThreads(repository, pullRequestId.number, p)
      }).loadAll(it)
    }.logError(LOG, "Error occurred while loading review threads")

  override fun createReview(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            event: GHPullRequestReviewEvent?,
                            body: String?,
                            commitSha: String?,
                            comments: List<GHPullRequestDraftReviewComment>?): CompletableFuture<GHPullRequestPendingReview> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.create(repository.serverPath, pullRequestId.id, event, body,
                                                                      commitSha, comments))
    }.logError(LOG, "Error occurred while creating review")

  override fun submitReview(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            reviewId: String,
                            event: GHPullRequestReviewEvent,
                            body: String?): CompletableFuture<out Any?> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.submit(repository.serverPath, reviewId, event, body))
    }.logError(LOG, "Error occurred while submitting review")

  override fun updateReviewBody(progressIndicator: ProgressIndicator,
                                reviewId: String,
                                newText: String): CompletableFuture<GHPullRequestReview> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.updateBody(repository.serverPath, reviewId, newText))
    }.logError(LOG, "Error occurred while updating review")

  override fun deleteReview(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            reviewId: String): CompletableFuture<out Any?> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.delete(repository.serverPath, reviewId))
    }.logError(LOG, "Error occurred while deleting review")

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier,
                          reviewId: String,
                          replyToCommentId: String,
                          body: String): CompletableFuture<GHPullRequestReviewCommentWithPendingReview> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(
        GHGQLRequests.PullRequest.Review.addComment(repository.serverPath,
                                                    reviewId,
                                                    replyToCommentId,
                                                    body))
    }.logError(LOG, "Error occurred while adding review thread reply")

  override fun addComment(progressIndicator: ProgressIndicator, reviewId: String,
                          body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<GHPullRequestReviewCommentWithPendingReview> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(progressIndicator,
                              GHGQLRequests.PullRequest.Review.addComment(repository.serverPath,
                                                                          reviewId,
                                                                          body, commitSha, fileName,
                                                                          diffLine))
    }.logError(LOG, "Error occurred while adding review comment")

  override fun deleteComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.deleteComment(repository.serverPath, commentId))
    }.logError(LOG, "Error occurred while deleting review comment")

  override fun updateComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String, newText: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.updateComment(repository.serverPath, commentId, newText))
    }.logError(LOG, "Error occurred while updating review comment")

  override fun resolveThread(progressIndicator: ProgressIndicator,
                             pullRequestId: GHPRIdentifier,
                             id: String): CompletableFuture<GHPullRequestReviewThread> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.resolveThread(repository.serverPath, id))
    }.logError(LOG, "Error occurred while resolving review thread")

  override fun unresolveThread(progressIndicator: ProgressIndicator,
                               pullRequestId: GHPRIdentifier,
                               id: String): CompletableFuture<GHPullRequestReviewThread> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.unresolveThread(repository.serverPath, id))
    }.logError(LOG, "Error occurred while unresolving review thread")

  companion object {
    private val LOG = logger<GHPRReviewService>()
  }
}