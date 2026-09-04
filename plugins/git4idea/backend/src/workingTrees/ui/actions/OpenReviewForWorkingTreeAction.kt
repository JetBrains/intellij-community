// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys.SELECTED_REVIEW

internal class OpenReviewForWorkingTreeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.getData(SELECTED_REVIEW) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val review = e.getData(SELECTED_REVIEW) ?: return
    review.open()
  }
}
