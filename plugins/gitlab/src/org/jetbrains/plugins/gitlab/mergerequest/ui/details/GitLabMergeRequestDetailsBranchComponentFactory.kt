// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.util.bindText
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.components.JBLabel
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import javax.swing.JComponent
import javax.swing.JLabel

// TODO: implement branch popup
internal object GitLabMergeRequestDetailsBranchComponentFactory {
  private const val COMPONENTS_GAP = 4

  fun create(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    val targetBranchComponent = createBranchLabel(scope, detailsInfoVm.targetBranch)
    val sourceBranchComponent = createBranchLabel(scope, detailsInfoVm.sourceBranch)

    return HorizontalListPanel(COMPONENTS_GAP).apply {
      add(targetBranchComponent)
      add(JLabel(AllIcons.Chooser.Left))
      add(sourceBranchComponent)
    }
  }

  private fun createBranchLabel(scope: CoroutineScope, branchName: Flow<@NlsContexts.Label String>): JBLabel {
    return JBLabel(CollaborationToolsIcons.Review.Branch).apply {
      bindText(scope, branchName)
    }.also {
      CollaborationToolsUIUtil.overrideUIDependentProperty(it) {
        foreground = CurrentBranchComponent.TEXT_COLOR
      }
    }
  }
}