// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.util.ResultUtil.processErrorAndGet
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.flow.fold
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

class GHPRReviewServiceImpl(private val securityService: GHPRSecurityService,
                            private val requestExecutor: GithubApiRequestExecutor,
                            private val repository: GHRepositoryCoordinates) : GHPRReviewService {
  override fun canComment() = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)

  override suspend fun loadPendingReview(pullRequestId: GHPRIdentifier): GHPullRequestPendingReviewDTO? =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.pendingReviews(repository.serverPath, pullRequestId.id)).nodes.singleOrNull()
    }.processErrorAndGet {
      LOG.info("Error occurred while loading pending review", it)
    }

  override suspend fun loadReviewThreads(pullRequestId: GHPRIdentifier): List<GHPullRequestReviewThread> =
    runCatching {
      ApiPageUtil.createGQLPagesFlow {
        requestExecutor.executeSuspend(GHGQLRequests.PullRequest.reviewThreads(repository, pullRequestId.number, it))
      }.fold(mutableListOf<GHPullRequestReviewThread>()) { acc, value ->
        acc.addAll(value.nodes)
        acc
      }
    }.processErrorAndGet {
      LOG.info("Error occurred while loading review threads", it)
    }

  override suspend fun createReview(pullRequestId: GHPRIdentifier,
                                    event: GHPullRequestReviewEvent?,
                                    body: String?,
                                    commitSha: String?,
                                    threads: List<GHPullRequestDraftReviewThread>?): GHPullRequestPendingReviewDTO =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.create(repository.serverPath, pullRequestId.id, event, body,
                                                                             commitSha, threads))
    }.processErrorAndGet {
      LOG.info("Error occurred while creating review", it)
    }

  override suspend fun submitReview(pullRequestId: GHPRIdentifier, reviewId: String, event: GHPullRequestReviewEvent, body: String?) {
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.submit(repository.serverPath, reviewId, event, body))
    }.processErrorAndGet {
      LOG.info("Error occurred while submitting review", it)
    }
  }

  override suspend fun updateReviewBody(reviewId: String, newText: String): GHPullRequestReview =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.updateBody(repository.serverPath, reviewId, newText))
    }.processErrorAndGet {
      LOG.info("Error occurred while updating review", it)
    }

  override suspend fun deleteReview(pullRequestId: GHPRIdentifier, reviewId: String) {
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.delete(repository.serverPath, reviewId))
    }.processErrorAndGet {
      LOG.info("Error occurred while deleting review", it)
    }
  }

  override suspend fun addComment(pullRequestId: GHPRIdentifier, reviewId: String, replyToCommentId: String, body: String)
    : GHPullRequestReviewComment = runCatching {
    requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.addComment(repository.serverPath,
                                                                               reviewId,
                                                                               replyToCommentId,
                                                                               body))
  }.processErrorAndGet {
    LOG.info("Error occurred while adding review thread reply", it)
  }

  override suspend fun addComment(reviewId: String, body: String, commitSha: String, fileName: String, diffLine: Int)
    : GHPullRequestReviewComment = runCatching {
    requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.addComment(repository.serverPath,
                                                                               reviewId,
                                                                               body, commitSha, fileName,
                                                                               diffLine))
  }.processErrorAndGet {
    LOG.info("Error occurred while adding review comment", it)
  }

  override suspend fun deleteComment(pullRequestId: GHPRIdentifier, commentId: String): GHPullRequestPendingReviewDTO =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.deleteComment(repository.serverPath, commentId))
    }.processErrorAndGet {
      LOG.info("Error occurred while deleting review comment", it)
    }

  override suspend fun updateComment(pullRequestId: GHPRIdentifier, commentId: String, newText: String): GHPullRequestReviewComment =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.updateComment(repository.serverPath, commentId, newText))
    }.processErrorAndGet {
      LOG.info("Error occurred while updating review comment", it)
    }

  override suspend fun addThread(reviewId: String, body: String, line: Int, side: Side, startLine: Int, fileName: String)
    : GHPullRequestReviewThread = runCatching {
    requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.addThread(repository.serverPath, reviewId, body, line, side, startLine,
                                                                              fileName))
  }.processErrorAndGet {
    LOG.info("Error occurred while adding review thread", it)
  }

  override suspend fun resolveThread(pullRequestId: GHPRIdentifier, id: String): GHPullRequestReviewThread =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.resolveThread(repository.serverPath, id))
    }.processErrorAndGet {
      LOG.info("Error occurred while resolving review thread", it)
    }

  override suspend fun unresolveThread(pullRequestId: GHPRIdentifier, id: String): GHPullRequestReviewThread =
    runCatching {
      requestExecutor.executeSuspend(GHGQLRequests.PullRequest.Review.unresolveThread(repository.serverPath, id))
    }.processErrorAndGet {
      LOG.info("Error occurred while unresolving review thread", it)
    }
}

private val LOG = logger<GHPRReviewService>()
