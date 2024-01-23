// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.computationStateIn
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createPendingReviewRequestsFlow

interface GHPRReviewViewModel {
  /**
   * Pending pull request review for a current user
   */
  val pendingReview: StateFlow<ComputedResult<GHPullRequestPendingReview?>>

  /**
   * UI handler for review submission request
   */
  var submitReviewInputHandler: (suspend (GHPRSubmitReviewViewModel) -> Unit)?

  /**
   * Request review submission
   *
   * [submitReviewInputHandler] has to be set before invoking this method
   */
  fun submitReview()

  companion object {
    val DATA_KEY: DataKey<GHPRReviewViewModel> = DataKey.create("GitHub.PullRequests.Review.ViewModel")
  }
}

internal class DelegatingGHPRReviewViewModel(private val helper: GHPRReviewViewModelHelper) : GHPRReviewViewModel {
  override val pendingReview: StateFlow<ComputedResult<GHPullRequestPendingReview?>> = helper.pendingReviewState
  override var submitReviewInputHandler: (suspend (GHPRSubmitReviewViewModel) -> Unit)? = null

  override fun submitReview() {
    val handler = submitReviewInputHandler
    checkNotNull(handler) { "UI handler was not set" }
    helper.submitReview(handler)
  }
}

internal class GHPRReviewViewModelHelper(parentCs: CoroutineScope, private val dataProvider: GHPRDataProvider) {
  private val cs = parentCs.childScope()
  private val reviewData = dataProvider.reviewData

  val pendingReviewState: StateFlow<ComputedResult<GHPullRequestPendingReview?>> =
    reviewData.createPendingReviewRequestsFlow().computationStateIn(cs)

  fun submitReview(handler: (suspend (GHPRSubmitReviewViewModel) -> Unit)) {
    val pendingReviewResult = pendingReviewState.value.result ?: return
    cs.launch {
      val ctx = currentCoroutineContext()
      val viewerIsAuthor = try {
        dataProvider.detailsData.loadDetails().await().viewerDidAuthor
      }
      catch (e: Exception) {
        LOG.info("Details loading failed", e)
        return@launch
      }
      val vm = GHPRSubmitReviewViewModelImpl(this, reviewData, viewerIsAuthor, pendingReviewResult.getOrNull()) {
        ctx.cancel()
      }
      handler.invoke(vm)
    }
  }

  init {
    reviewData.resetPendingReview()
  }

  companion object {
    private val LOG = logger<GHPRReviewViewModelHelper>()
  }
}