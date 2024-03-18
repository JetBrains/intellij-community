// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.review.CodeReviewSubmitPopupHandler
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRSubmitReviewPopup : CodeReviewSubmitPopupHandler<GHPRSubmitReviewViewModel>() {
  override fun CoroutineScope.createActionsComponent(vm: GHPRSubmitReviewViewModel): JPanel {
    val cs = this
    val buttons = buildList<JComponent> {
      if (!vm.viewerIsAuthor) {
        object : InstallButton(GithubBundle.message("pull.request.review.submit.approve.button"), true) {
          override fun setTextAndSize() {}
        }.apply {
          bindDisabledIn(cs, vm.isBusy)
          addActionListener { vm.submit(GHPullRequestReviewEvent.APPROVE) }
        }.let(::add)

        JButton(GithubBundle.message("pull.request.review.submit.request.changes")).apply {
          isOpaque = false
        }.apply {
          bindDisabledIn(cs, vm.isBusy)
          addActionListener { vm.submit(GHPullRequestReviewEvent.REQUEST_CHANGES) }
        }.let(::add)
      }

      JButton(GithubBundle.message("pull.request.review.submit.comment.button")).apply {
        isOpaque = false
        toolTipText = GithubBundle.message("pull.request.review.submit.comment.description")
      }.apply {
        bindDisabledIn(cs, vm.isBusy)
        addActionListener { vm.submit(GHPullRequestReviewEvent.COMMENT) }
      }.let(::add)
    }
    return HorizontalListPanel(ACTIONS_GAP).apply {
      buttons.forEach { add(it) }
    }
  }

  override fun createTitleActionsComponentIn(cs: CoroutineScope, vm: GHPRSubmitReviewViewModel): JComponent {
    val defaultActions = super.createTitleActionsComponentIn(cs, vm)
    if (!vm.hasPendingReview) {
      return defaultActions
    }

    val discardButton = InlineIconButton(
      icon = CollaborationToolsIcons.Delete,
      hoveredIcon = CollaborationToolsIcons.DeleteHovered,
      tooltip = GithubBundle.message("pull.request.discard.pending.comments")
    ).apply {
      border = JBUI.Borders.empty(5)
      bindDisabledIn(cs, vm.isBusy)
      bindVisibilityIn(cs, vm.draftCommentsCount.map { it > 0 })
    }.also { button ->
      button.actionListener = ActionListener {
        val discardButtonMessageDialog = MessageDialogBuilder.yesNo(
          GithubBundle.message("pull.request.discard.pending.comments.dialog.title"),
          GithubBundle.message("pull.request.discard.pending.comments.dialog.msg")
        )
        if (discardButtonMessageDialog.ask(button)) {
          vm.discard()
        }
      }
    }

    return HorizontalListPanel(TITLE_ACTIONS_GAP).apply {
      add(discardButton)
      add(defaultActions)
    }
  }
}