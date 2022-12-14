// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer

interface GHPRReviewFlowViewModel {
  val requestedReviewersState: StateFlow<List<GHPullRequestRequestedReviewer>>
  val reviewerAndReviewState: StateFlow<Map<GHPullRequestRequestedReviewer, ReviewState>>

  enum class ReviewState {
    ACCEPTED,
    WAIT_FOR_UPDATES,
    NEED_REVIEW,
  }
}