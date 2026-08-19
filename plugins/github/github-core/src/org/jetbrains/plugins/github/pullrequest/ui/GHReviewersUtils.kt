// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState

object GHReviewersUtils {
  /**
   * Computes the state of every reviewer of a pull request, matching how GitHub presents them.
   *
   * The result merges two sources the way GitHub does:
   * - each reviewer's effective review state ([reviews]), resolved by [calcReviewerState] — surfaced as approved /
   *   changes-requested / commented;
   * - the pending review requests ([reviewers]) — a request always supersedes a prior review, so a reviewer who
   *   already reviewed and is requested again becomes [GHPRReviewerState.RE_REQUESTED], while a reviewer who never
   *   reviewed becomes [GHPRReviewerState.NEEDS_REVIEW].
   *
   * The PR [author]'s own reviews are excluded.
   */
  fun getReviewsByReviewers(
    author: GHActor?,
    reviews: List<GHPullRequestReview>,
    reviewers: List<GHPullRequestRequestedReviewer>,
    ghostUser: GHUser,
  ): Map<GHPullRequestRequestedReviewer, GHPRReviewerState> {
    val result = LinkedHashMap<GHPullRequestRequestedReviewer, GHPRReviewerState>()

    reviews
      .groupBy { it.author as? GHPullRequestRequestedReviewer ?: ghostUser }
      .forEach { (reviewer, reviewerReviews) ->
        if (reviewer == author) return@forEach
        val state = calcReviewerState(reviewerReviews) ?: return@forEach
        result[reviewer] = state
      }

    reviewers.forEach { requestedReviewer ->
      result[requestedReviewer] = if (result.containsKey(requestedReviewer)) {
        GHPRReviewerState.RE_REQUESTED
      }
      else {
        GHPRReviewerState.NEEDS_REVIEW
      }
    }

    return result
  }

  /**
   * Resolves a reviewer's effective state from all of their reviews, the way GitHub web does.
   *
   * GitHub only ever surfaces four reviewer states — review requested / approved / changes-requested / commented —
   * so the most recent *opinionated* review (approved or changes-requested) wins, and any plain comments submitted
   * afterwards do not override it. A [GHPullRequestReviewState.DISMISSED] review is terminal, though: once the
   * *latest* review is dismissed, it is surfaced as a plain comment and does not let an older opinionated review
   * resurface (you can't dismiss a comment, so a plain [GHPullRequestReviewState.COMMENTED] review never hides one).
   *
   * Returns [GHPRReviewerState.COMMENTED] when the reviewer only ever left (or had dismissed) comments, and `null`
   * when they have no submitted review at all (e.g. only an in-progress [GHPullRequestReviewState.PENDING] draft),
   * so such a reviewer is not surfaced.
   */
  private fun calcReviewerState(reviews: List<GHPullRequestReview>): GHPRReviewerState? {
    var commented = false
    for (review in reviews.sortedByDescending { it.createdAt }) {
      when (review.state) {
        GHPullRequestReviewState.APPROVED -> return GHPRReviewerState.APPROVED
        GHPullRequestReviewState.CHANGES_REQUESTED -> return GHPRReviewerState.CHANGES_REQUESTED
        GHPullRequestReviewState.DISMISSED -> return GHPRReviewerState.COMMENTED
        GHPullRequestReviewState.COMMENTED -> commented = true
        GHPullRequestReviewState.PENDING -> Unit // review not submitted yet, keep looking
      }
    }
    return if (commented) GHPRReviewerState.COMMENTED else null
  }
}
