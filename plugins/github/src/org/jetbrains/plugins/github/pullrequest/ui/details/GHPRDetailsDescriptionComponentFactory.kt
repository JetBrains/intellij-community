// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsDescriptionComponentFactory {
  private const val DESCRIPTION_MAX_HEIGHT = 30

  fun create(scope: CoroutineScope, reviewDetailsVM: GHPRDetailsViewModel): JComponent {
    val descriptionPanel = HtmlEditorPane().apply {
      bindText(scope, reviewDetailsVM.descriptionState)
    }

    val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
      val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return@ActionLink
      ActionUtil.invokeAction(action, it.source as ActionLink, ActionPlaces.UNKNOWN, null, null)
    }.apply {
      border = JBUI.Borders.emptyTop(4)
    }

    val layout = MigLayout(LC().emptyBorders().flowY())
    return JPanel(layout).apply {
      isOpaque = false

      add(descriptionPanel, CC().maxHeight("${JBUI.scale(DESCRIPTION_MAX_HEIGHT)}"))
      add(timelineLink, CC())
    }
  }
}