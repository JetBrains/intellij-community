// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CHECKIN_PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.actions.CommonCheckinProjectAction
import com.intellij.openapi.vcs.actions.getProjectCommitWorkflowHandler
import com.intellij.openapi.vcs.actions.isFromLocalChanges
import com.intellij.openapi.vcs.actions.isProjectUsesNonModalCommit

internal val isToggleCommitUi = Registry.get("vcs.non.modal.commit.toggle.ui")

private class ToggleChangesViewCommitUiAction : DumbAwareToggleAction() {
  // need to use `CommonCheckinProjectAction` inheritor - otherwise action is invisible in Local Changes
  private val commitProjectAction = object : CommonCheckinProjectAction() {}

  init {
    copyFrom(this, ACTION_CHECKIN_PROJECT)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    when {
      !e.isProjectUsesNonModalCommit() || !isToggleCommitUi.asBoolean() || !e.isFromLocalChanges() ->
        e.presentation.isEnabledAndVisible = false

      isSelected(e) -> e.presentation.isEnabledAndVisible = true
      else -> commitProjectAction.update(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = e.getProjectCommitWorkflowHandler()?.isActive == true

  override fun setSelected(e: AnActionEvent, state: Boolean) =
    if (state) {
      commitProjectAction.actionPerformed(e)
    }
    else {
      e.getProjectCommitWorkflowHandler()!!.deactivate(false)
    }
}