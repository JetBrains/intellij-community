// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.async.throwFailure
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.Avatar
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.codereview.list.search.SimpleSelectablePopupItemRenderer
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

// TODO: implement scenario with multiple reviewers
internal class GitLabMergeRequestRequestReviewAction(
  private val scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.request")) {
  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.userCanManage) { isBusy, userCanManageReview ->
        isEnabled = !isBusy && userCanManageReview
      }
    }
  }

  override fun actionPerformed(event: ActionEvent) {
    val parentComponent = event.source as? JComponent ?: return
    val point = RelativePoint.getSouthWestOf(parentComponent)
    scope.launch {
      val reviewers = reviewFlowVm.reviewers.value
      val selectedUser = ChooserPopupUtil.showAsyncChooserPopup(
        point,
        reviewFlowVm.potentialReviewers.throwFailure(),
        filteringMapper = { user -> user.username },
        renderer = SimpleSelectablePopupItemRenderer.create { reviewer ->
          ChooserPopupUtil.SelectablePopupItemPresentation.Simple(
            reviewer.username,
            avatarIconsProvider.getIcon(reviewer, Avatar.Sizes.BASE),
            null,
            isSelected = reviewers.any { it.id == reviewer.id }
          )
        }
      )

      // TODO: replace on CollectionDelta
      if (selectedUser != null) {
        if (reviewers.any { it.id == selectedUser.id }) {
          reviewFlowVm.removeReviewer(selectedUser)
        }
        else {
          reviewFlowVm.setReviewers(listOf(selectedUser))
        }
      }
    }
  }
}
