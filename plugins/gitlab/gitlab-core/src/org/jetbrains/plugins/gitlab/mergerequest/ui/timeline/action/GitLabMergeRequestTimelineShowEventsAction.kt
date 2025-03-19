// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineController

class GitLabMergeRequestTimelineShowEventsAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val available = e.getData(GitLabMergeRequestTimelineController.DATA_KEY) != null
    e.presentation.isEnabledAndVisible = available
    if (available) {
      super.update(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = e.getRequiredData(GitLabMergeRequestTimelineController.DATA_KEY).showEvents

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getRequiredData(GitLabMergeRequestTimelineController.DATA_KEY).showEvents = state
  }
}