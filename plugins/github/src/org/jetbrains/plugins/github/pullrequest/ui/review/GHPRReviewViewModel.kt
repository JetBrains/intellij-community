// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.util.ComputedResult
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider

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
  override val pendingReview: StateFlow<ComputedResult<GHPullRequestPendingReview?>> = helper.pendingReviewState.asStateFlow()
  override var submitReviewInputHandler: (suspend (GHPRSubmitReviewViewModel) -> Unit)? = null

  override fun submitReview() {
    val handler = submitReviewInputHandler
    checkNotNull(handler) { "UI handler was not set" }
    helper.submitReview(handler)
  }
}

internal class GHPRReviewViewModelHelper(parentCs: CoroutineScope,
                                         private val dataProvider: GHPRReviewDataProvider,
                                         private val viewerIsAuthor: Boolean) {
  private val cs = parentCs.childScope()

  val pendingReviewState = MutableStateFlow<ComputedResult<GHPullRequestPendingReview?>>(ComputedResult.loading())

  fun submitReview(handler: (suspend (GHPRSubmitReviewViewModel) -> Unit)) {
    val pendingReviewResult = pendingReviewState.value.result ?: return
    cs.launch {
      val ctx = currentCoroutineContext()
      val vm = GHPRSubmitReviewViewModelImpl(this, dataProvider, viewerIsAuthor, pendingReviewResult.getOrNull()) {
        ctx.cancel()
      }
      handler.invoke(vm)
    }
  }

  init {
    dataProvider.addPendingReviewListener(cs.nestedDisposable()) {
      pendingReviewState.value = ComputedResult.loading()
      dataProvider.loadPendingReview().handle { res, err ->
        if (err != null) {
          pendingReviewState.value = ComputedResult.failure(err.cause ?: err)
        }
        else {
          pendingReviewState.value = ComputedResult.success(res)
        }
      }
    }
    dataProvider.resetPendingReview()
  }
}