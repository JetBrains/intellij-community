// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.ui.codereview.details.data.ReviewState

/**
 * Detailed state of a single reviewer of a pull request, mirroring how GitHub itself presents reviewers.
 *
 * Unlike the platform-wide [ReviewState] (which only distinguishes accepted / waiting-for-updates / need-review),
 * this captures every state a reviewer can be shown in on the GitHub PR page, so the plugin stays consistent with it:
 * - [APPROVED], [CHANGES_REQUESTED] — the reviewer submitted an opinionated review;
 * - [COMMENTED] — the reviewer submitted a review with comments only (no approval/rejection), or their opinionated
 *   review was later dismissed (GitHub then shows it as a plain comment — you can't dismiss a comment);
 * - [NEEDS_REVIEW] — a review was requested and the reviewer has not reviewed yet;
 * - [RE_REQUESTED] — the reviewer already reviewed, but a fresh review was requested again from them.
 *
 * The distinction between [NEEDS_REVIEW]/[RE_REQUESTED] (the PR is waiting on this reviewer) and
 * [COMMENTED] (no action is pending from this reviewer) is surfaced in the UI.
 */
enum class GHPRReviewerState {
  APPROVED,
  CHANGES_REQUESTED,
  COMMENTED,
  NEEDS_REVIEW,
  RE_REQUESTED;

  /**
   * `true` when the pull request is still awaiting a review from this reviewer.
   */
  val isAwaitingReview: Boolean
    get() = this == NEEDS_REVIEW || this == RE_REQUESTED
}

/**
 * Collapses the detailed reviewer state into the platform-wide [ReviewState] used by cross-provider components.
 *
 * States that are neither an approval nor an explicit request for updates ([GHPRReviewerState.COMMENTED] and the
 * awaiting-review states) map to [ReviewState.NEED_REVIEW], since the PR does not (yet) have this reviewer's approval.
 */
fun GHPRReviewerState.toReviewState(): ReviewState = when (this) {
  GHPRReviewerState.APPROVED -> ReviewState.ACCEPTED
  GHPRReviewerState.CHANGES_REQUESTED -> ReviewState.WAIT_FOR_UPDATES
  GHPRReviewerState.COMMENTED,
  GHPRReviewerState.NEEDS_REVIEW,
  GHPRReviewerState.RE_REQUESTED -> ReviewState.NEED_REVIEW
}
