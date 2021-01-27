// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CHECKIN_PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.actions.isProjectUsesNonModalCommit
import com.intellij.vcs.commit.CommitWorkflowManager.Companion.setCommitFromLocalChanges

private class SwitchToCommitDialogAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.isProjectUsesNonModalCommit()
  }

  override fun actionPerformed(e: AnActionEvent) {
    setCommitFromLocalChanges(false)

    val commitAction = ActionManager.getInstance().getAction(ACTION_CHECKIN_PROJECT) ?: return
    invokeAction(commitAction, e.dataContext, e.place, e.inputEvent, null)
  }
}