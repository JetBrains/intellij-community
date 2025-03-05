// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.defaultButton
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabMergeRequestCloseCreateTabAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.action.GitLabMergeRequestCreateAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModel
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

internal object GitLabMergeRequestCreateActionsComponentFactory {
  private const val BUTTONS_GAP = 10

  fun create(project: Project, cs: CoroutineScope, createVm: GitLabMergeRequestCreateViewModel): JComponent {
    val createAction = GitLabMergeRequestCreateAction(cs, createVm)
    val closeAction = GitLabMergeRequestCloseCreateTabAction(project)

    return HorizontalListPanel(BUTTONS_GAP).apply {
      add(createAction.toButton().defaultButton())
      add(closeAction.toButton())
    }
  }

  private fun Action.toButton(): JButton {
    val action = this
    return JButton(action).apply {
      isOpaque = false
    }
  }
}