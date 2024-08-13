// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService

private val LOG = logger<GHPRReviewDataProviderImpl>()

internal class GHPRReviewDataProviderImpl(parentCs: CoroutineScope,
                                          private val reviewService: GHPRReviewService,
                                          private val changesProvider: GHPRChangesDataProvider,
                                          private val pullRequestId: GHPRIdentifier,
                                          private val messageBus: MessageBus)
  : GHPRReviewDataProvider {

  private val cs = parentCs.childScope(javaClass.name)

  override val pendingReviewComment: MutableStateFlow<String> = MutableStateFlow("")

  override fun canComment() = reviewService.canComment()

  private val threadsLoader = LoaderWithMutableCache(cs) { reviewService.loadReviewThreads(pullRequestId) }
  override val threadsNeedReloadSignal = threadsLoader.updatedSignal

  override suspend fun loadThreads(): List<GHPullRequestReviewThread> = threadsLoader.load()
  override suspend fun signalThreadsNeedReload() = threadsLoader.clearCache()


  private val reviewLoader = LoaderWithMutableCache(cs) { reviewService.loadPendingReview(pullRequestId)?.toModel() }
  override val pendingReviewNeedsReloadSignal = reviewLoader.updatedSignal

  override suspend fun loadPendingReview(): GHPullRequestPendingReview? = reviewLoader.load()
  override suspend fun signalPendingReviewNeedsReload() = reviewLoader.clearCache()

  // TODO: load created threads and add to the loaded
  override suspend fun createReview(event: GHPullRequestReviewEvent?,
                                    body: String?,
                                    commitSha: String?,
                                    threads: List<GHPullRequestDraftReviewThread>?): GHPullRequestPendingReview {
    val review = reviewService.createReview(pullRequestId, event, body, commitSha, threads).toModel()
    withContext(NonCancellable) {
      if (event == null) {
        reviewLoader.overrideResult(review)
      }
      if (!threads.isNullOrEmpty()) {
        signalThreadsNeedReload()
      }
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
    return review
  }

  override suspend fun createReview(event: GHPullRequestReviewEvent, body: String?): GHPullRequestPendingReview {
    val review = reviewService.createReview(pullRequestId, event, body).toModel()
    withContext(NonCancellable) {
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
    return review
  }

  // TODO: change loaded threads statuses
  override suspend fun submitReview(reviewId: String, event: GHPullRequestReviewEvent, body: String?) {
    reviewService.submitReview(pullRequestId, reviewId, event, body)
    withContext(NonCancellable) {
      signalPendingReviewNeedsReload()
      signalThreadsNeedReload()
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
  }

  // TODO: remove loaded threads
  override suspend fun deleteReview(reviewId: String) {
    reviewService.deleteReview(pullRequestId, reviewId)
    withContext(NonCancellable) {
      updateReview(reviewId) { null }
      signalThreadsNeedReload()
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
  }

  override suspend fun updateReviewBody(reviewId: String, newText: String): String {
    val review = reviewService.updateReviewBody(reviewId, newText)
    withContext(NonCancellable) {
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewUpdated(reviewId, newText)
      }
    }
    return review.body
  }

  override suspend fun addComment(reviewId: String,
                                  body: String,
                                  commitSha: String,
                                  fileName: String,
                                  side: Side,
                                  line: Int): GHPullRequestReviewComment {
    val patch = changesProvider.loadPatchFromMergeBase(commitSha, fileName)
    check(patch != null && patch is TextFilePatch) { "Cannot find diff between $commitSha and merge base" }
    val position = PatchHunkUtil.findDiffFileLineIndex(patch, side to line) ?: error("Can't map file line to diff")
    val comment = reviewService.addComment(reviewId, body, commitSha, fileName, position)
    withContext(NonCancellable) {
      updateReview(reviewId) { it.copy(commentsCount = it.commentsCount + 1) }
      signalThreadsNeedReload()
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
    return comment
  }

  // TODO: add comment to a loaded thread
  override suspend fun addComment(replyToCommentId: String, body: String): GHPullRequestReviewComment {
    val reviewId = loadPendingReview()?.id
    val comment = if (reviewId == null) {
      // not having a review will produce a security error
      val review = reviewService.createReview(pullRequestId)
      val comment = reviewService.addComment(pullRequestId, review.id, replyToCommentId, body)
      reviewService.submitReview(pullRequestId, review.id, GHPullRequestReviewEvent.COMMENT, null)
      comment
    }
    else {
      val comment = reviewService.addComment(pullRequestId, reviewId, replyToCommentId, body)
      withContext(NonCancellable) {
        updateReview(reviewId) { it.copy(commentsCount = it.commentsCount + 1) }
      }
      comment
    }
    withContext(NonCancellable) {
      signalThreadsNeedReload()
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
    return comment
  }

  override suspend fun deleteComment(commentId: String) {
    val commentReview = reviewService.deleteComment(pullRequestId, commentId)
    withContext(NonCancellable) {
      // if deleted from current pending review, potentially clear the review
      updateReview(commentReview.id) { commentReview.takeIf { (it.comments.totalCount ?: 0) > 0 }?.toModel() }
      updateThreads { removeComment(it, commentId) }
      signalPendingReviewNeedsReload()
    }
  }

  override suspend fun updateComment(commentId: String, newText: String): GHPullRequestReviewComment {
    val comment = reviewService.updateComment(pullRequestId, commentId, newText)
    withContext(NonCancellable) {
      updateThreads { updateCommentBody(it, commentId, comment.body) }
    }
    return comment
  }

  override suspend fun createThread(reviewId: String, body: String, line: Int, side: Side, startLine: Int, fileName: String)
    : GHPullRequestReviewThread {
    try {
      val thread = reviewService.addThread(reviewId, body, line, side, startLine, fileName)
      withContext(NonCancellable) {
        updateThreads { it + thread }
        updateReview(reviewId) { it.copy(commentsCount = it.commentsCount + 1) }
      }
      return thread
    }
    finally {
      withContext(Dispatchers.Main) {
        messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC).onReviewsChanged()
      }
    }
  }

  override suspend fun resolveThread(id: String): GHPullRequestReviewThread {
    val thread = reviewService.resolveThread(pullRequestId, id)
    withContext(NonCancellable) {
      updateThreads { list -> list.map { if (it.id == thread.id) thread else it } }
    }
    return thread
  }

  override suspend fun unresolveThread(id: String): GHPullRequestReviewThread {
    val thread = reviewService.unresolveThread(pullRequestId, id)
    withContext(NonCancellable) {
      updateThreads { list -> list.map { if (it.id == thread.id) thread else it } }
    }
    return thread
  }

  private suspend fun updateReview(updater: (GHPullRequestPendingReview?) -> GHPullRequestPendingReview?) {
    reviewLoader.updateLoaded {
      try {
        updater(it)
      }
      catch (e: Exception) {
        LOG.warn("Failed to update pending review data after mutation", e)
        it
      }
    }
  }

  private suspend fun updateReview(reviewId: String, updater: (GHPullRequestPendingReview) -> GHPullRequestPendingReview?) {
    updateReview { if (it?.id == reviewId) updater(it) else it }
  }

  private suspend fun updateThreads(updater: (List<GHPullRequestReviewThread>) -> List<GHPullRequestReviewThread>) {
    threadsLoader.updateLoaded {
      try {
        updater(it)
      }
      catch (e: Exception) {
        LOG.warn("Failed to update review threads data after mutation", e)
        it
      }
    }
  }
}

@VisibleForTesting
internal fun GHPullRequestPendingReviewDTO.toModel(): GHPullRequestPendingReview =
  GHPullRequestPendingReview(id, state, comments.totalCount ?: 0)

private fun updateCommentBody(threads: List<GHPullRequestReviewThread>, commentId: String, newBody: String) =
  threads.map {
    it.copy(commentsNodes = GraphQLNodesDTO(it.comments.map { comment ->
      if (comment.id == commentId) comment.copy(body = newBody) else comment
    }))
  }

private fun removeComment(threads: List<GHPullRequestReviewThread>,
                          commentId: String): List<GHPullRequestReviewThread> = threads.mapNotNull {
  val comments = it.comments.filter { comment -> comment.id != commentId }
  if (comments.isEmpty())
    null
  else
    it.copy(commentsNodes = GraphQLNodesDTO(comments))
}