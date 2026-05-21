// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Encapsulates a single AI review session with its own state, problems, and lifecycle.
 */
@ApiStatus.Internal
class AIReviewSession(
  val project: Project,
  internal val cs: CoroutineScope,
  val sessionId: String,
  val request: AIReviewRequest.LocalChanges,
  val agent: AIReviewAgent?,
) {
  @Volatile
  private var cancelRunningReview: (() -> Unit)? = null

  @Volatile
  private var retryReviewRequest: ((AIReviewRequest.LocalChanges) -> Unit)? = null

  val viewModel: AIReviewViewModel = AIReviewViewModel(project, request)
  val problemsHolder: AIReviewProblemsHolder = AIReviewProblemsHolder()

  val agentDisplayName: @Nls String
    get() = agent?.displayName ?: AIReviewBundle.message("aiReview.agent.display.name.default")

  fun registerRunningReview(cancel: () -> Unit) {
    cancelRunningReview = cancel
  }

  fun registerRetryReview(requestReview: (AIReviewRequest.LocalChanges) -> Unit) {
    retryReviewRequest = requestReview
  }

  fun clearRunningReview() {
    cancelRunningReview = null
  }

  fun hasRunningReview(): Boolean = cancelRunningReview != null

  fun canRetryReview(request: AIReviewRequest?): Boolean {
    return request is AIReviewRequest.LocalChanges && retryReviewRequest != null
  }

  fun cancelRunningReview() {
    cancelRunningReview?.invoke()
  }

  fun retryReview(request: AIReviewRequest?) {
    val localChangesRequest = request as? AIReviewRequest.LocalChanges ?: return
    retryReviewRequest?.invoke(localChangesRequest)
  }

  fun cancelAnalysis() {
    val partialReview = (viewModel.state.value as? AIReviewViewModel.State.RequestHolder)?.request?.let {
      AIReviewResult(it, emptyList())
    }
    viewModel.setCancelledState(partialReview)
  }
}
