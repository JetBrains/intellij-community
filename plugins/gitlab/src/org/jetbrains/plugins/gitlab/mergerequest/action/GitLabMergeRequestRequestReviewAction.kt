// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.Avatar
import com.intellij.collaboration.ui.codereview.list.search.ChooserPopupUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.popup.PopupState
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListCellRenderer

// TODO: implement scenario with multiple reviewers
internal class GitLabMergeRequestRequestReviewAction(
  private val scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.request")) {
  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.userCanManageReview) { isBusy, userCanManageReview ->
        isEnabled = !isBusy && userCanManageReview
      }
    }
  }

  override fun actionPerformed(event: ActionEvent) {
    val popupState: PopupState<JBPopup> = PopupState.forPopup()
    val parentComponent = event.source as? JComponent ?: return
    val point = RelativePoint.getSouthWestOf(parentComponent)
    scope.launch {
      val users = reviewFlowVm.getPotentialReviewers()

      val selectedUser = ChooserPopupUtil.showChooserPopup(
        point,
        popupState,
        users,
        filteringMapper = { user -> user.username },
        renderer = ReviewerRenderer(reviewFlowVm, avatarIconsProvider)
      )

      // TODO: replace on CollectionDelta
      if (selectedUser != null) {
        val reviewers = reviewFlowVm.reviewers.value
        if (selectedUser in reviewers) {
          reviewFlowVm.removeReviewer(selectedUser)
        }
        else {
          reviewFlowVm.setReviewers(listOf(selectedUser))
        }
      }
    }
  }

  private class ReviewerRenderer(
    private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    private val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ) : ListCellRenderer<GitLabUserDTO> {
    private val checkBox: JBCheckBox = JBCheckBox().apply {
      isOpaque = false
    }
    private val label: SimpleColoredComponent = SimpleColoredComponent()
    private val panel = BorderLayoutPanel(10, 5).apply {
      addToLeft(checkBox)
      addToCenter(label)
      border = JBUI.Borders.empty(5)
    }

    override fun getListCellRendererComponent(list: JList<out GitLabUserDTO>,
                                              value: GitLabUserDTO,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      checkBox.apply {
        this.isSelected = value in reviewFlowVm.reviewers.value
        this.isFocusPainted = cellHasFocus
        this.isFocusable = cellHasFocus
      }

      label.apply {
        clear()
        append(value.username)
        icon = avatarIconsProvider.getIcon(value, Avatar.Sizes.BASE)
        foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
      }

      UIUtil.setBackgroundRecursively(panel, ListUiUtil.WithTallRow.background(list, isSelected, true))

      return panel
    }
  }
}

