// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentWithPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.GithubAsyncUtil.extractError
import org.jetbrains.plugins.github.util.GithubAsyncUtil.isCancellation
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

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
    }.logError("Error occurred while loading pending review")

  override fun loadReviewThreads(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier) =
    progressManager.submitIOTask(progressIndicator) {
      SimpleGHGQLPagesLoader(requestExecutor, { p ->
        GHGQLRequests.PullRequest.reviewThreads(repository, pullRequestId.number, p)
      }).loadAll(it)
    }.logError("Error occurred while loading review threads")

  override fun createReview(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            event: GHPullRequestReviewEvent,
                            body: String?): CompletableFuture<out Any?> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(progressIndicator,
                                GHGQLRequests.PullRequest.Review.create(repository.serverPath, pullRequestId.id, event, body))
      }.notify(pullRequestId)
      .logError("Error occurred while creating review")

  override fun submitReview(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            reviewId: String,
                            event: GHPullRequestReviewEvent,
                            body: String?): CompletableFuture<out Any?> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(progressIndicator,
                                GHGQLRequests.PullRequest.Review.submit(repository.serverPath, reviewId, event, body))
      }.notify(pullRequestId)
      .logError("Error occurred while submitting review")

  override fun deleteReview(progressIndicator: ProgressIndicator,
                            pullRequestId: GHPRIdentifier,
                            reviewId: String): CompletableFuture<out Any?> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(progressIndicator,
                                GHGQLRequests.PullRequest.Review.delete(repository.serverPath, reviewId))
      }.notify(pullRequestId)
      .logError("Error occurred while deleting review")

  override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.getCommentBody(repository.serverPath, commentId))
    }.logError("Error occurred while loading comment source")

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier,
                          body: String,
                          replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(
          GithubApiRequests.Repos.PullRequests.Comments.createReply(repository, pullRequestId.number, replyToCommentId, body))
      }.notify(pullRequestId)
      .logError("Error occurred while adding review thread reply")

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(
          GithubApiRequests.Repos.PullRequests.Comments.create(repository, pullRequestId.number, commitSha, fileName, diffLine, body))
      }.notify(pullRequestId)
      .logError("Error occurred while creating single comment review")

  override fun addComment(progressIndicator: ProgressIndicator,
                          pullRequestId: GHPRIdentifier, reviewId: String?,
                          body: String, commitSha: String, fileName: String, diffLine: Int)
    : CompletableFuture<GHPullRequestReviewCommentWithPendingReview> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(progressIndicator,
                                GHGQLRequests.PullRequest.Review.addComment(repository.serverPath,
                                                                            pullRequestId.id, reviewId,
                                                                            body, commitSha, fileName,
                                                                            diffLine))
      }.notify(pullRequestId)
      .logError("Error occurred while adding review comment")

  override fun deleteComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.deleteComment(repository.serverPath, commentId))
    }.logError("Error occurred while deleting review comment")

  override fun updateComment(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier, commentId: String, newText: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(GHGQLRequests.PullRequest.Review.updateComment(repository.serverPath, commentId, newText))
    }.logError("Error occurred while updating review comment")

  override fun resolveThread(progressIndicator: ProgressIndicator,
                             pullRequestId: GHPRIdentifier,
                             id: String): CompletableFuture<GHPullRequestReviewThread> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(GHGQLRequests.PullRequest.Review.resolveThread(repository.serverPath, id))
      }
      .logError("Error occurred while resolving review thread")

  override fun unresolveThread(progressIndicator: ProgressIndicator,
                               pullRequestId: GHPRIdentifier,
                               id: String): CompletableFuture<GHPullRequestReviewThread> =
    progressManager.submitIOTask(progressIndicator) {
        requestExecutor.execute(GHGQLRequests.PullRequest.Review.unresolveThread(repository.serverPath, id))
      }
      .logError("Error occurred while unresolving review thread")

  private fun <T> CompletableFuture<T>.notify(pullRequestId: GHPRIdentifier): CompletableFuture<T> =
    handle(BiFunction<T, Throwable?, T> { result: T, error: Throwable? ->
      try {
        messageBus.syncPublisher(GHPRDataContext.PULL_REQUEST_EDITED_TOPIC).onPullRequestReviewsEdited(pullRequestId)
      }
      catch (e: Exception) {
        LOG.info("Error occurred while updating pull request review data", e)
      }

      if (error != null) throw extractError(error)
      result
    })

  companion object {
    private val LOG = logger<GHPRReviewService>()

    fun <T> CompletableFuture<T>.logError(message: String): CompletableFuture<T> =
      handle(BiFunction<T, Throwable?, T> { result: T, error: Throwable? ->
        if (error != null) {
          val actualError = extractError(error)
          if (!isCancellation(actualError)) LOG.info(message, actualError)
          throw actualError
        }
        result
      })
  }
}