// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.codereview.details.data.ReviewState
import icons.CollaborationToolsIcons
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Color
import javax.swing.Icon

/**
 * Maps a [GHPRReviewerState] to the icon, avatar-border color and label used to present a reviewer,
 * so the plugin matches how GitHub shows each reviewer state.
 *
 * The status icon carries the "is the PR waiting on this reviewer?" signal:
 * awaiting-review states use the warning icon, while [GHPRReviewerState.COMMENTED]
 * uses a neutral icon so the two situations are visually distinct.
 */
object GHPRReviewerStateUIUtil {
  fun getStatusIcon(state: GHPRReviewerState): Icon = when (state) {
    GHPRReviewerState.APPROVED -> ReviewDetailsUIUtil.getReviewStateIcon(ReviewState.ACCEPTED)
    GHPRReviewerState.CHANGES_REQUESTED -> ReviewDetailsUIUtil.getReviewStateIcon(ReviewState.WAIT_FOR_UPDATES)
    GHPRReviewerState.NEEDS_REVIEW, GHPRReviewerState.RE_REQUESTED -> ReviewDetailsUIUtil.getReviewStateIcon(ReviewState.NEED_REVIEW)
    GHPRReviewerState.COMMENTED -> CollaborationToolsIcons.Comment
  }

  fun getBorderColor(state: GHPRReviewerState): Color = when (state) {
    GHPRReviewerState.APPROVED -> Avatar.Color.ACCEPTED_BORDER
    GHPRReviewerState.CHANGES_REQUESTED -> Avatar.Color.WAIT_FOR_UPDATES_BORDER
    GHPRReviewerState.COMMENTED,
    GHPRReviewerState.NEEDS_REVIEW,
    GHPRReviewerState.RE_REQUESTED -> Avatar.Color.NEED_REVIEW_BORDER
  }

  fun getText(state: GHPRReviewerState, reviewer: @Nls String): @Nls String = when (state) {
    GHPRReviewerState.APPROVED -> CollaborationToolsBundle.message("review.details.status.reviewer.approved", reviewer)
    GHPRReviewerState.CHANGES_REQUESTED -> CollaborationToolsBundle.message("review.details.status.reviewer.wait.for.updates", reviewer)
    GHPRReviewerState.NEEDS_REVIEW -> CollaborationToolsBundle.message("review.details.status.reviewer.need.review", reviewer)
    GHPRReviewerState.RE_REQUESTED -> GithubBundle.message("pull.request.reviewer.state.re.requested", reviewer)
    GHPRReviewerState.COMMENTED -> GithubBundle.message("pull.request.reviewer.state.commented", reviewer)
  }
}
