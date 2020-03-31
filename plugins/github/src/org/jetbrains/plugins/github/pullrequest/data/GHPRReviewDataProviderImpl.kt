// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.GHNodes
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GithubPullRequestCommentWithHtml
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProviderUtil.futureOfMutableOnEDT
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.util.LazyCancellableBackgroundProcessValue
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture

class GHPRReviewDataProviderImpl(private val reviewService: GHPRReviewService, private val pullRequestId: GHPRIdentifier)
  : GHPRReviewDataProvider {

  private val pendingReviewRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadPendingReview(it, pullRequestId)
  }

  private val reviewThreadsRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadReviewThreads(it, pullRequestId)
  }

  override fun loadPendingReview() = futureOfMutableOnEDT { pendingReviewRequestValue.value }

  override fun resetPendingReview() = pendingReviewRequestValue.drop()

  override fun loadReviewThreads() = futureOfMutableOnEDT { reviewThreadsRequestValue.value }

  override fun resetReviewThreads() = reviewThreadsRequestValue.drop()

  override fun submitReview(progressIndicator: ProgressIndicator,
                            reviewId: String?,
                            event: GHPullRequestReviewEvent,
                            body: String?): CompletableFuture<out Any?> {
    val future = if (reviewId != null) reviewService.submitReview(progressIndicator, pullRequestId, reviewId, event, body)
    else reviewService.createReview(progressIndicator, pullRequestId, event, body)

    pendingReviewRequestValue.overrideProcess(future.handleOnEdt { _, error ->
      if (error != null) {
        ApplicationManager.getApplication().invokeLater {
          pendingReviewRequestValue.drop()
        }
        throw ProcessCanceledException()
      }
      null
    })
    return future
  }

  override fun deleteReview(progressIndicator: ProgressIndicator, reviewId: String): CompletableFuture<out Any?> {
    val future = reviewService.deleteReview(progressIndicator, pullRequestId, reviewId)
    pendingReviewRequestValue.overrideProcess(future.handleOnEdt { _, error ->
      if (error != null) {
        ApplicationManager.getApplication().invokeLater {
          pendingReviewRequestValue.drop()
        }
        throw ProcessCanceledException()
      }
      null
    })
    return future
  }

  override fun canComment() = reviewService.canComment()

  override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<String> {
    return reviewService.getCommentMarkdownBody(progressIndicator, commentId)
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return reviewService.addComment(progressIndicator, pullRequestId, body, commitSha, fileName, diffLine)
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          reviewId: String?,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<out GHPullRequestReviewComment> {
    val future = reviewService.addComment(progressIndicator, pullRequestId, reviewId, body, commitSha, fileName,
                                          diffLine)
    pendingReviewRequestValue.overrideProcess(future.handleOnEdt { result, error ->
      if (error != null) {
        ApplicationManager.getApplication().invokeLater {
          pendingReviewRequestValue.drop()
        }
        throw ProcessCanceledException()
      }
      result.pullRequestReview
    })
    return future
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          body: String,
                          replyToCommentId: Long): CompletableFuture<GithubPullRequestCommentWithHtml> {
    return reviewService.addComment(progressIndicator, pullRequestId, body, replyToCommentId)
  }

  override fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<out Any> {
    val future = reviewService.deleteComment(progressIndicator, pullRequestId, commentId)

    pendingReviewRequestValue.overrideProcess(future.handleOnEdt { result, error ->
      if (error != null || (result.state != GHPullRequestReviewState.PENDING || result.comments.totalCount != 0)) {
        ApplicationManager.getApplication().invokeLater {
          pendingReviewRequestValue.drop()
        }
        throw ProcessCanceledException()
      }
      null
    })
    reviewThreadsRequestValue.combineResult(future) { list, _ ->
      list.mapNotNull {
        val comments = it.comments.filter { comment -> comment.id != commentId }
        if (comments.isEmpty()) null else GHPullRequestReviewThread(it.id, it.isResolved, GHNodes(comments))
      }
    }

    return future
  }

  override fun updateComment(progressIndicator: ProgressIndicator, commentId: String, newText: String)
    : CompletableFuture<GHPullRequestReviewComment> {
    val future = reviewService.updateComment(progressIndicator, pullRequestId, commentId, newText)
    reviewThreadsRequestValue.combineResult(future) { list, newComment ->
      list.map {
        GHPullRequestReviewThread(it.id, it.isResolved, GHNodes(it.comments.map { comment ->
          if (comment.id == commentId)
            GHPullRequestReviewComment(comment.id, comment.databaseId, comment.url, comment.author, newComment.bodyHtml, comment.createdAt,
                                       comment.state, comment.path, comment.commit, comment.position,
                                       comment.originalCommit, comment.originalPosition, comment.replyTo, comment.diffHunk,
                                       GHNode(comment.reviewId), comment.viewerCanDelete, comment.viewerCanUpdate)
          else comment
        }))
      }
    }
    return future
  }

  override fun resolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread> {
    val future = reviewService.resolveThread(progressIndicator, pullRequestId, id)
    reviewThreadsRequestValue.combineResult(future) { list, thread ->
      list.map { if (it == thread) thread else it }
    }
    return future
  }

  override fun unresolveThread(progressIndicator: ProgressIndicator, id: String): CompletableFuture<GHPullRequestReviewThread> {
    val future = reviewService.unresolveThread(progressIndicator, pullRequestId, id)
    reviewThreadsRequestValue.combineResult(future) { list, thread ->
      list.map { if (it == thread) thread else it }
    }
    return future
  }

  override fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit) =
    reviewThreadsRequestValue.addDropEventListener(disposable, listener)

  override fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit) =
    pendingReviewRequestValue.addDropEventListener(disposable, listener)
}