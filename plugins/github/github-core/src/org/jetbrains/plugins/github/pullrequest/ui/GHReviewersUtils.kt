// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState

object GHReviewersUtils {
  fun getReviewsByReviewers(
    author: GHActor?,
    reviews: List<GHPullRequestReview>,
    reviewers: List<GHPullRequestRequestedReviewer>,
    ghostUser: GHUser
  ): Map<GHPullRequestRequestedReviewer, ReviewState> {
    val result = mutableMapOf<GHPullRequestRequestedReviewer, ReviewState>()
    reviews.associate { (it.author as? GHPullRequestRequestedReviewer ?: ghostUser) to it.state } // latest review state by reviewer
      .forEach { (reviewer, reviewState) ->
        if (reviewer != author) {
          if (reviewState == GHPullRequestReviewState.APPROVED) {
            result[reviewer] = ReviewState.ACCEPTED
          }
          if (reviewState == GHPullRequestReviewState.CHANGES_REQUESTED) {
            result[reviewer] = ReviewState.WAIT_FOR_UPDATES
          }
        }
      }

    reviewers.forEach { requestedReviewer ->
      result[requestedReviewer] = ReviewState.NEED_REVIEW
    }
    return result
  }
}