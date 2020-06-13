// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.GHNodes
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService
import org.jetbrains.plugins.github.util.*
import java.util.concurrent.CompletableFuture

class GHPRReviewDataProviderImpl(private val reviewService: GHPRReviewService,
                                 private val pullRequestId: GHPRIdentifier,
                                 private val messageBus: MessageBus)
  : GHPRReviewDataProvider, Disposable {

  override val submitReviewCommentDocument by lazy(LazyThreadSafetyMode.NONE) { EditorFactory.getInstance().createDocument("") }

  private val pendingReviewRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadPendingReview(it, pullRequestId)
  }

  private val reviewThreadsRequestValue = LazyCancellableBackgroundProcessValue.create {
    reviewService.loadReviewThreads(it, pullRequestId)
  }

  override fun loadPendingReview() = pendingReviewRequestValue.value

  override fun resetPendingReview() = pendingReviewRequestValue.drop()

  override fun loadReviewThreads() = reviewThreadsRequestValue.value

  override fun resetReviewThreads() = reviewThreadsRequestValue.drop()

  override fun createReview(progressIndicator: ProgressIndicator,
                            event: GHPullRequestReviewEvent?,
                            body: String?,
                            commitSha: String?,
                            comments: List<GHPullRequestDraftReviewComment>?): CompletableFuture<GHPullRequestPendingReview> {
    val future = reviewService.createReview(progressIndicator, pullRequestId, event, body, commitSha, comments).notifyReviews()
    if (event == null) {
      pendingReviewRequestValue.overrideProcess(future.errorOnEdt { throw ProcessCanceledException() }.successOnEdt { it })
    }
    return if (comments.isNullOrEmpty()) future else future.dropReviews()
  }

  override fun submitReview(progressIndicator: ProgressIndicator,
                            reviewId: String,
                            event: GHPullRequestReviewEvent,
                            body: String?): CompletableFuture<out Any?> {
    val future = reviewService.submitReview(progressIndicator, pullRequestId, reviewId, event, body)
    pendingReviewRequestValue.overrideProcess(future.errorOnEdt { throw ProcessCanceledException() }.successOnEdt { null })
    return future.dropReviews().notifyReviews()
  }

  override fun deleteReview(progressIndicator: ProgressIndicator, reviewId: String): CompletableFuture<out Any?> {
    val future = reviewService.deleteReview(progressIndicator, pullRequestId, reviewId)
    pendingReviewRequestValue.combineResult(future) { pendingReview, _ ->
      if (pendingReview != null && pendingReview.id == reviewId) throw ProcessCanceledException()
      else pendingReview
    }
    return future.dropReviews().notifyReviews()
  }

  override fun canComment() = reviewService.canComment()

  override fun getCommentMarkdownBody(progressIndicator: ProgressIndicator, commentId: String) =
    reviewService.getCommentMarkdownBody(progressIndicator, commentId)

  override fun addComment(progressIndicator: ProgressIndicator,
                          reviewId: String,
                          body: String,
                          commitSha: String,
                          fileName: String,
                          diffLine: Int): CompletableFuture<out GHPullRequestReviewComment> {
    val future =
      reviewService.addComment(progressIndicator, reviewId, body, commitSha, fileName, diffLine)

    pendingReviewRequestValue.overrideProcess(future.errorOnEdt { throw ProcessCanceledException() }.successOnEdt { it.pullRequestReview })
    return future.dropReviews().notifyReviews()
  }

  override fun addComment(progressIndicator: ProgressIndicator,
                          replyToCommentId: String,
                          body: String): CompletableFuture<out GHPullRequestReviewComment> {
    return pendingReviewRequestValue.value.thenCompose {
      val reviewId = it?.id
      if (reviewId == null) {
        createReview(progressIndicator).thenCompose { review ->
          reviewService.addComment(progressIndicator, pullRequestId, review.id, replyToCommentId, body).thenCompose { comment ->
            submitReview(progressIndicator, review.id, GHPullRequestReviewEvent.COMMENT, null).thenApply {
              comment
            }
          }.dropReviews().notifyReviews()
        }
      }
      else {
        val future = reviewService.addComment(progressIndicator, pullRequestId, reviewId, replyToCommentId, body)
        pendingReviewRequestValue.overrideProcess(
          future.errorOnEdt { throw ProcessCanceledException() }.successOnEdt { it.pullRequestReview })
        future.dropReviews().notifyReviews()
      }
    }
  }


  override fun deleteComment(progressIndicator: ProgressIndicator, commentId: String): CompletableFuture<out Any> {
    val future = reviewService.deleteComment(progressIndicator, pullRequestId, commentId)

    pendingReviewRequestValue.overrideProcess(future.handleOnEdt { result, error ->
      if (error != null || (result?.state != GHPullRequestReviewState.PENDING || result.comments.totalCount != 0)) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
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
                                       comment.reviewId?.let { GHNode(it) }, comment.viewerCanDelete, comment.viewerCanUpdate)
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

  private fun <T> CompletableFuture<T>.notifyReviews(): CompletableFuture<T> =
    completionOnEdt {
      messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
    }

  private fun <T> CompletableFuture<T>.dropReviews(): CompletableFuture<T> =
    completionOnEdt {
      reviewThreadsRequestValue.drop()
    }

  override fun addReviewThreadsListener(disposable: Disposable, listener: () -> Unit) =
    reviewThreadsRequestValue.addDropEventListener(disposable, listener)

  override fun addPendingReviewListener(disposable: Disposable, listener: () -> Unit) =
    pendingReviewRequestValue.addDropEventListener(disposable, listener)

  override fun dispose() {
    pendingReviewRequestValue.drop()
    reviewThreadsRequestValue.drop()
  }
}