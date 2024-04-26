// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.JButtonAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPROnCurrentBranchService
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRSubmitReviewPopup
import javax.swing.JButton

class GHPRReviewSubmitAction
  : JButtonAction(StringUtil.ELLIPSIS, GithubBundle.message("pull.request.review.submit.action.description")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    with(e.presentation) {
      description = GithubBundle.message("pull.request.review.submit.action.description")
      val vm = findVm(e)
      if (vm == null) {
        isEnabledAndVisible = false
        return
      }

      isVisible = true
      val pendingReviewResult = vm.pendingReview.value
      isEnabled = !pendingReviewResult.isInProgress

      if (isEnabledAndVisible) {
        val review = pendingReviewResult.result?.getOrNull()
        text = getText(e.place, review?.commentsCount)
        putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, review != null)
      }
      else {
        text = getText(e.place, null)
        putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, false)
      }
    }
  }

  @NlsSafe
  private fun getText(place: String, pendingComments: Int?): String {
    if (ActionPlaces.isPopupPlace(place) && !place.contains("gitlab", true)) {
      return CollaborationToolsBundle.message("review.start.submit.action")
    }

    return if (pendingComments == null) {
      CollaborationToolsBundle.message("review.start.submit.action.short")
    }
    else {
      CollaborationToolsBundle.message("review.start.submit.action.short.with.comments", pendingComments)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = findVm(e) ?: return
    val project = e.project
    val component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT)

    vm.submitReviewInputHandler = {
      withContext(Dispatchers.Main) {
        when {
          component != null -> GHPRSubmitReviewPopup.show(it, component)
          project != null -> GHPRSubmitReviewPopup.show(it, project)
        }
      }
    }
    vm.submitReview()
  }

  /**
   * Tries to find the review VM in the data context or in a branch widget service
   */
  private fun findVm(e: AnActionEvent): GHPRReviewViewModel? =
    e.getData(GHPRReviewViewModel.DATA_KEY) ?: e.project?.serviceIfCreated<GHPROnCurrentBranchService>()?.vmState?.value

  override fun updateButtonFromPresentation(button: JButton, presentation: Presentation) {
    super.updateButtonFromPresentation(button, presentation)
    ClientProperty.put(button, DarculaButtonUI.DEFAULT_STYLE_KEY, presentation.getClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY))
  }
}