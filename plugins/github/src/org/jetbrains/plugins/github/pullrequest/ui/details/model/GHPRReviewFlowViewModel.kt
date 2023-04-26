// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewFlowViewModel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import javax.swing.JComponent

internal interface GHPRReviewFlowViewModel : CodeReviewFlowViewModel<GHPullRequestRequestedReviewer> {
  val isBusy: Flow<Boolean>
  val requestedReviewers: Flow<List<GHPullRequestRequestedReviewer>>
  val reviewState: Flow<ReviewState>
  val role: Flow<ReviewRole>
  val pendingComments: Flow<Int>

  val userCanManageReview: Boolean
  val userCanMergeReview: Boolean

  val isMergeAllowed: Flow<Boolean>
  val isRebaseAllowed: Flow<Boolean>
  val isSquashMergeAllowed: Flow<Boolean>

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