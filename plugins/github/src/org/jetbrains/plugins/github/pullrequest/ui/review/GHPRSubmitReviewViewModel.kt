// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.ui.codereview.review.CodeReviewSubmitViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider

interface GHPRSubmitReviewViewModel : CodeReviewSubmitViewModel {
  /**
   * If the current user is the author of the pull request
   */
  val viewerIsAuthor: Boolean

  /**
   * If there's a currently pending review
   */
  val hasPendingReview: Boolean

  /**
   * Discard the current pending review
   */
  fun discard()

  /**
   * Submit the review with a specified event
   */
  fun submit(event: GHPullRequestReviewEvent)
}

internal class GHPRSubmitReviewViewModelImpl(parentCs: CoroutineScope,
                                             private val dataProvider: GHPRReviewDataProvider,
                                             override val viewerIsAuthor: Boolean,
                                             private val pendingReview: GHPullRequestPendingReview?,
                                             private val onDone: () -> Unit)
  : GHPRSubmitReviewViewModel {
  private val cs = parentCs.childScope()

  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val hasPendingReview: Boolean = pendingReview != null

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy
  private val _error = MutableStateFlow<Throwable?>(null)

  override val error: StateFlow<Throwable?> = _error.asStateFlow()

  override val draftCommentsCount: StateFlow<Int> = MutableStateFlow(pendingReview?.commentsCount ?: 0)

  override val text: MutableStateFlow<String> = dataProvider.pendingReviewComment

  override fun submit(event: GHPullRequestReviewEvent) {
    val body = text.value
    taskLauncher.launch {
      if (pendingReview != null) {
        dataProvider.submitReview(pendingReview.id, event, body)
      }
      else {
        dataProvider.createReview(event, body)
      }
      text.value = ""
      onDone()
    }
  }

  override fun discard() {
    if (pendingReview != null) {
      taskLauncher.launch {
        dataProvider.deleteReview(pendingReview.id)
        onDone()
      }
    }
  }

  override fun cancel() {
    onDone()
  }
}