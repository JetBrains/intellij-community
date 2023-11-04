// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.inverted
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.review.CodeReviewSubmitPopupHandler
import com.intellij.collaboration.ui.util.*
import com.intellij.ide.plugins.newui.InstallButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JButton
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
    return HorizontalListPanel(ACTIONS_GAP).apply {
      add(approveButton)
      add(unApproveButton)
      add(submitButton)
    }
  }
}