// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import javax.swing.JComponent
import javax.swing.JLabel

// TODO: implement another statuses
internal object GitLabMergeRequestDetailsStatusChecksComponentFactory {
  private const val STATUSES_GAP = 10
  private const val STATUS_COMPONENT_BORDER = 5

  fun create(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    return VerticalListPanel(STATUSES_GAP).apply {
      add(createConflictsComponent(scope, detailsInfoVm))
    }
  }

  private fun createConflictsComponent(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Review conflicts label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.conflicts")
      bindVisibility(scope, detailsInfoVm.hasConflicts)
    }
  }
}