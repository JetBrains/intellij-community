// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.details.ReviewRole
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.ide.plugins.newui.InstallButton
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestApproveAction
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestCloseAction
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestReopenAction
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestReviewResumeAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import javax.swing.JButton
import javax.swing.JComponent

// TODO: implement MR actions
internal object GitLabMergeRequestDetailsActionsComponentFactory {
  fun create(scope: CoroutineScope, reviewFlowVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    return Wrapper().apply {
      bindContent(scope, reviewFlowVm.role.map { role ->
        when (role) {
          ReviewRole.AUTHOR -> createActionsForAuthor(scope, reviewFlowVm)
          ReviewRole.REVIEWER -> createActionsForReviewer(scope, reviewFlowVm)
          ReviewRole.GUEST -> createActionsForGuest()
        }
      })
    }
  }

  private fun createActionsForAuthor(scope: CoroutineScope, reviewFlowVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    val closeReviewActionButton = JButton(GitLabMergeRequestCloseAction(scope, reviewFlowVm)).apply {
      isOpaque = false
    }
    val reopenReviewActionButton = JButton(GitLabMergeRequestReopenAction(scope, reviewFlowVm)).apply {
      isOpaque = false
    }

    return HorizontalListPanel(gap = 10).apply {
      add(closeReviewActionButton)
      add(reopenReviewActionButton)
    }
  }

  private fun createActionsForReviewer(scope: CoroutineScope, reviewFlowVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    val mergeButton = object : InstallButton(true) {
      override fun setTextAndSize() {}
    }.apply {
      action = GitLabMergeRequestApproveAction(scope, reviewFlowVm)
    }
    val resumeReviewButton = JButton(GitLabMergeRequestReviewResumeAction(scope, reviewFlowVm))

    return HorizontalListPanel(gap = 10).apply {
      add(mergeButton)
      add(resumeReviewButton)
    }
  }

  private fun createActionsForGuest() = HorizontalListPanel()
}