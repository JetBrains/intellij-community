// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.data.ReviewRole
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewFlowViewModel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import javax.swing.JComponent

interface GHPRReviewFlowViewModel : CodeReviewFlowViewModel<GHPullRequestRequestedReviewer>, GHPRReviewViewModel {
  val isBusy: Flow<Boolean>
  val requestedReviewers: Flow<List<GHPullRequestRequestedReviewer>>
  val reviewState: Flow<ReviewState>
  val role: Flow<ReviewRole>
  val pendingComments: Flow<Int>

  /**
   * Represents the repository restrictions for pull requests
   */
  val repositoryRestrictions: RepositoryRestrictions

  /**
   * Flag indicating whether the user can perform all actions related to manage the review
   */
  val userCanManageReview: Boolean

  /**
   * Flag indicating whether the user can perform all actions related to merge the review
   */
  val userCanMergeReview: Boolean

  /**
   * Determines that all the conditions for "Merge" action are done
   */
  val isMergeEnabled: Flow<Boolean>

  /**
   * Determines that all the conditions for "Squash and Merge" action are done
   */
  val isSquashMergeEnabled: Flow<Boolean>

  /**
   * Determines that all the conditions for "Rebase" action are done
   */
  val isRebaseEnabled: Flow<Boolean>

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

class RepositoryRestrictions(securityService: GHPRSecurityService) {
  /**
   * Determines whether "Merge" is allowed based on repository settings
   */
  val isMergeAllowed: Boolean = securityService.isMergeAllowed()

  /**
   * Determines whether "Squash and Merge" is allowed based on repository settings
   */
  val isSquashMergeAllowed: Boolean = securityService.isSquashMergeAllowed()

  /**
   * Determines whether "Rebase" is allowed based on repository settings
   */
  val isRebaseAllowed: Boolean = securityService.isRebaseMergeAllowed()
}