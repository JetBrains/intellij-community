// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import javax.swing.JComponent
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsDescriptionComponentFactory {
  private const val VISIBLE_DESCRIPTION_LINES = 2

  fun create(
    scope: CoroutineScope,
    detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel
  ): JComponent {
    val descriptionPanel = SimpleHtmlPane().apply {
      bindText(scope, detailsInfoVm.description)
    }
    val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
      detailsInfoVm.showTimeline()
    }.apply {
      border = JBUI.Borders.emptyTop(4)
    }

    val layout = MigLayout(LC().emptyBorders().flowY())
    return JPanel(layout).apply {
      name = "Review details description panel"
      isOpaque = false

      val maxHeight = UIUtil.getUnscaledLineHeight(descriptionPanel) * VISIBLE_DESCRIPTION_LINES
      add(descriptionPanel, CC().maxHeight("$maxHeight"))
      add(timelineLink, CC())
    }
  }
}