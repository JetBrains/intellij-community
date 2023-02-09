// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.collaboration.ui.codereview.details.ReviewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import javax.swing.JComponent

internal interface GHPRReviewFlowViewModel {
  val isBusy: Flow<Boolean>
  val requestedReviewersState: StateFlow<List<GHPullRequestRequestedReviewer>>
  val reviewerAndReviewState: StateFlow<Map<GHPullRequestRequestedReviewer, ReviewState>>
  val reviewState: Flow<ReviewState>
  val roleState: StateFlow<ReviewRole>
  val pendingCommentsState: StateFlow<Int>

  val userCanManageReview: Boolean
  val userCanMergeReview: Boolean

  val isMergeAllowed: Boolean
  val isRebaseAllowed: Boolean
  val isSquashMergeAllowed: Boolean

  fun mergeReview()
  fun rebaseReview()
  fun squashAndMergeReview()

  fun closeReview()
  fun reopenReview()
  fun postDraftedReview()

  fun removeReviewer(reviewer: GHPullRequestRequestedReviewer)
  fun requestReview(parentComponent: JComponent)
  fun reRequestReview()
  fun setMyselfAsReviewer()
}