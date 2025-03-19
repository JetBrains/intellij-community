// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.ComputedResult
import com.intellij.diff.util.Side
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview

interface GHPRReviewDataProvider {

  //TODO: move to a shared VM
  val pendingReviewComment: MutableStateFlow<String>

  val pendingReviewNeedsReloadSignal: Flow<Unit>
  val threadsNeedReloadSignal: Flow<Unit>

  suspend fun loadPendingReview(): GHPullRequestPendingReview?
  suspend fun loadThreads(): List<GHPullRequestReviewThread>

  suspend fun signalPendingReviewNeedsReload()
  suspend fun signalThreadsNeedReload()

  suspend fun createReview(event: GHPullRequestReviewEvent, body: String? = null): GHPullRequestPendingReview

  suspend fun submitReview(reviewId: String, event: GHPullRequestReviewEvent, body: String? = null)

  suspend fun createReview(event: GHPullRequestReviewEvent? = null, body: String? = null,
                           commitSha: String? = null,
                           threads: List<GHPullRequestDraftReviewThread>? = null): GHPullRequestPendingReview

  suspend fun updateReviewBody(reviewId: String, newText: String): @NlsSafe String

  suspend fun deleteReview(reviewId: String)

  fun canComment(): Boolean

  suspend fun addComment(reviewId: String,
                         body: String,
                         commitSha: String,
                         fileName: String,
                         side: Side,
                         line: Int): GHPullRequestReviewComment

  suspend fun addComment(replyToCommentId: String, body: String): GHPullRequestReviewComment

  suspend fun deleteComment(commentId: String)

  suspend fun updateComment(commentId: String, newText: String): GHPullRequestReviewComment

  suspend fun createThread(reviewId: String,
                           body: String,
                           line: Int,
                           side: Side,
                           startLine: Int,
                           fileName: String): GHPullRequestReviewThread

  suspend fun resolveThread(id: String): GHPullRequestReviewThread

  suspend fun unresolveThread(id: String): GHPullRequestReviewThread
}

val GHPRReviewDataProvider.pendingReviewComputationFlow: Flow<ComputedResult<GHPullRequestPendingReview?>>
  get() = computationStateFlow(pendingReviewNeedsReloadSignal.withInitial(Unit)) { loadPendingReview() }

val GHPRReviewDataProvider.threadsComputationFlow: Flow<ComputedResult<List<GHPullRequestReviewThread>>>
  get() = computationStateFlow(threadsNeedReloadSignal.withInitial(Unit)) { loadThreads() }
