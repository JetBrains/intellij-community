// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindTextHtml
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import javax.swing.JComponent

internal object GHPRDetailsDescriptionComponentFactory {
  private const val VISIBLE_DESCRIPTION_LINES = 2

  fun create(scope: CoroutineScope, reviewDetailsVM: GHPRDetailsViewModel): JComponent {
    val descriptionPanel = SimpleHtmlPane().apply {
      bindTextHtml(scope, reviewDetailsVM.descriptionState)
      val group = ActionManager.getInstance().getAction("Github.PullRequest.Details.Popup") as ActionGroup
      PopupHandler.installPopupMenu(this, group, "GHPRDetailsPopup")
    }.let { editor ->
      CollaborationToolsUIUtil.wrapWithLimitedSize(editor, DimensionRestrictions.LinesHeight(editor, VISIBLE_DESCRIPTION_LINES))
    }

    val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
      val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return@ActionLink
      ActionUtil.invokeAction(action, it.source as ActionLink, ActionPlaces.UNKNOWN, null, null)
    }.apply {
      border = JBUI.Borders.emptyTop(4)
    }

    return VerticalListPanel().apply {
      name = "Review details description panel"

      add(descriptionPanel)
      add(timelineLink)
    }
  }
}