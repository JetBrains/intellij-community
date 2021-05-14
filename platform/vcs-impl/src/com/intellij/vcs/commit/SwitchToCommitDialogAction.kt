// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CHECKIN_PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil.invokeAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.commit.CommitModeManager.Companion.setCommitFromLocalChanges

private class SwitchToCommitDialogAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null &&
                                         CommitModeManager.isNonModalInSettings() &&
                                         CommitModeManager.getInstance(project).getCurrentCommitMode() != CommitMode.ModalCommitMode
  }

  override fun actionPerformed(e: AnActionEvent) {
    setCommitFromLocalChanges(e.project, false)

    val commitAction = ActionManager.getInstance().getAction(ACTION_CHECKIN_PROJECT) ?: return
    invokeAction(commitAction, e.dataContext, e.place, e.inputEvent, null)
  }
}