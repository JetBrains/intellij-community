// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create

import com.intellij.collaboration.async.throwFailure
import com.intellij.collaboration.ui.ComponentListPanelFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.Avatar
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsStatusComponentFactory
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.popup.ChooserPopupUtil
import com.intellij.collaboration.ui.util.popup.SelectablePopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SimpleSelectablePopupItemRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JLabel

internal object GitLabMergeRequestCreateReviewersComponentFactory {
  private const val HEADER_GAP = 5
  private const val REVIEWERS_GAP = 7
  private const val REVIEWER_PRESENTATION_GAP = 8
  private const val COMPONENTS_GAP = 5

  fun create(cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    val header = HorizontalListPanel(gap = HEADER_GAP).apply {
      val reviewersLabel = JLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        bindTextIn(cs, createVm.adjustedReviewers.map { reviewers ->
          GitLabBundle.message("merge.request.create.reviewers.label", reviewers.size)
        })
      }
      val editButton = CodeReviewCommentUIUtil.createEditButton { event ->
        val parentComponent = event.source as? JComponent ?: return@createEditButton
        val point = RelativePoint.getSouthWestOf(parentComponent)
        cs.launch {
          adjustReviewer(point, createVm)
        }
      }

      add(reviewersLabel)
      add(editButton)
    }
    val reviewersComponent = ComponentListPanelFactory.createVertical(
      parentCs = cs,
      items = createVm.adjustedReviewers,
      itemKeyExtractor = { reviewer -> reviewer.id },
      gap = REVIEWERS_GAP,
      componentFactory = { _, reviewer -> createReviewerComponent(createVm, reviewer) }
    )

    return VerticalListPanel(gap = COMPONENTS_GAP).apply {
      add(header)
      add(reviewersComponent)
    }
  }

  private fun createReviewerComponent(createVm: GitLabMergeRequestCreateViewModel, reviewer: GitLabUserDTO): JComponent {
    return CodeReviewDetailsStatusComponentFactory.ReviewDetailsStatusLabel("GitLab create MR: reviewer").apply {
      iconTextGap = REVIEWER_PRESENTATION_GAP
      icon = createVm.avatarIconProvider.getIcon(reviewer, Avatar.Sizes.BASE)
      text = reviewer.name
    }
  }

  private suspend fun adjustReviewer(point: RelativePoint, createVm: GitLabMergeRequestCreateViewModel) {
    val reviewers = createVm.adjustedReviewers.value
    val selectedUser = ChooserPopupUtil.showAsyncChooserPopup(
      point,
      createVm.potentialReviewers.throwFailure(),
      filteringMapper = { user -> user.username },
      renderer = SimpleSelectablePopupItemRenderer.create { reviewer ->
        SelectablePopupItemPresentation.Simple(
          reviewer.username,
          createVm.avatarIconProvider.getIcon(reviewer, Avatar.Sizes.BASE),
          null,
          isSelected = reviewers.any { it.id == reviewer.id }
        )
      }
    )

    if (selectedUser != null) {
      if (reviewers.any { it.id == selectedUser.id }) {
        createVm.removeReviewer(selectedUser)
      }
      else {
        createVm.addReviewer(selectedUser)
      }
    }
  }
}