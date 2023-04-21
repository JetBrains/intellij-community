// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindText
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsDescriptionComponentFactory {
  fun create(
    scope: CoroutineScope,
    detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel,
    openTimeLineAction: (GitLabMergeRequestId, Boolean) -> Unit
  ): JComponent {
    val descriptionPanel = SimpleHtmlPane().apply {
      bindText(scope, detailsInfoVm.description)
    }
    val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
      openTimeLineAction(GitLabMergeRequestId.Simple(detailsInfoVm.number), true)
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