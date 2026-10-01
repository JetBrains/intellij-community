// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.codereview.review.CodeReviewSubmitPopupHandler
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

internal object GitLabMergeRequestSubmitReviewPopup : CodeReviewSubmitPopupHandler<GitLabMergeRequestSubmitReviewViewModel>() {
  override fun CoroutineScope.createActionsComponent(vm: GitLabMergeRequestSubmitReviewViewModel): JPanel {
    val cs = this
    val approveButton = object : InstallButton(GitLabBundle.message("merge.request.approve.action"), true) {
      init {
        toolTipText = GitLabBundle.message("merge.request.approve.action.tooltip")
        bindVisibilityIn(cs, vm.isApproved.inverted())
        bindDisabledIn(cs, vm.isBusy)

        addActionListener {
          vm.approve()
        }
      }

      override fun setTextAndSize() {}
    }
    val unApproveButton = JButton(GitLabBundle.message("merge.request.revoke.action")).apply {
      isOpaque = false
      toolTipText = GitLabBundle.message("merge.request.revoke.action.tooltip")
      bindVisibilityIn(cs, vm.isApproved)
      bindDisabledIn(cs, vm.isBusy)
      addActionListener {
        vm.unApprove()
      }
    }
    val submitButton = JButton(CollaborationToolsBundle.message("review.submit.action")).apply {
      isOpaque = false
      toolTipText = GitLabBundle.message("merge.request.submit.action.tooltip")
      bindEnabledIn(cs, combine(vm.isBusy, vm.text, vm.draftCommentsCount) { busy, text, draftComments ->
        // Is enabled when not busy and: the text is not blank, or there are draft comments to submit
        !busy && (text.isNotBlank() || draftComments > 0)
      })
      addActionListener {
        vm.submit()
      }
    }
    val buttonsPanel = HorizontalListPanel(ACTIONS_GAP).apply {
      add(approveButton)
      add(unApproveButton)
      add(submitButton)
    }
    if (!vm.canDeleteWorktree) return buttonsPanel

    val deleteWorktreeCheckBox = JBCheckBox(GitLabBundle.message("merge.request.review.submit.delete.worktree"),
                                            vm.deleteWorktreeAfterSubmit.value).apply {
      isOpaque = false
      bindDisabledIn(cs, vm.isBusy)
      addActionListener { vm.setDeleteWorktreeAfterSubmit(isSelected) }
    }
    val deleteWorktreeComment = JLabel(GitBundle.message("Git.WorkingTrees.delete.current.worktree.project.will.be.closed")).apply {
      foreground = UIUtil.getContextHelpForeground()
      font = JBFont.small()
      border = JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(deleteWorktreeCheckBox))
    }
    val deleteWorktreePanel = VerticalListPanel().apply {
      add(deleteWorktreeCheckBox)
      add(deleteWorktreeComment)
    }
    return VerticalListPanel(DELETE_WORKTREE_GAP).apply {
      add(deleteWorktreePanel)
      add(buttonsPanel)
    }
  }

  private const val DELETE_WORKTREE_GAP = 6

  override val errorPresenter: ErrorStatusPresenter<Throwable> by lazy {
    ErrorStatusPresenter.simple(
      CollaborationToolsBundle.message("review.submit.failed"),
      descriptionProvider = ExceptionUtil::getPresentableMessage
    )
  }
}