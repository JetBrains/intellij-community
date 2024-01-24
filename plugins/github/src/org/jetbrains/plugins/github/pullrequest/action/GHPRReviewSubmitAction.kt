// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.JButtonAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRSubmitReviewPopup
import javax.swing.JButton

class GHPRReviewSubmitAction : JButtonAction(StringUtil.ELLIPSIS, GithubBundle.message("pull.request.review.submit.action.description")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    with(e.presentation) {
      description = GithubBundle.message("pull.request.review.submit.action.description")
      val vm = e.getData(GHPRReviewViewModel.DATA_KEY)
      if (vm == null) {
        isEnabledAndVisible = false
        return
      }

      isVisible = true
      val pendingReviewResult = vm.pendingReview.value
      isEnabled = !pendingReviewResult.isInProgress

      if (isEnabledAndVisible) {
        val review = pendingReviewResult.result?.getOrNull()
        text = getText(review?.commentsCount)
        putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, review != null)
      }
      else {
        text = getText(null)
        putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, false)
      }
    }
  }

  @NlsSafe
  private fun getText(pendingComments: Int?): String =
    if (pendingComments == null) {
      CollaborationToolsBundle.message("review.start.submit.action.short")
    }
    else {
      CollaborationToolsBundle.message("review.start.submit.action.short.with.comments", pendingComments)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getRequiredData(GHPRReviewViewModel.DATA_KEY)
    val parentComponent = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return

    vm.submitReviewInputHandler = {
      withContext(Dispatchers.Main) {
        GHPRSubmitReviewPopup.show(it, parentComponent)
      }
    }
    vm.submitReview()
  }

  override fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    super.updateButtonFromPresentation(button, presentation)
    ClientProperty.put(button, DarculaButtonUI.DEFAULT_STYLE_KEY, presentation.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY))
  }
}