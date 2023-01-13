// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabShowMergeRequestAction : DumbAwareAction(GitLabBundle.messagePointer("merge.request.show.action"),
                                                     GitLabBundle.messagePointer("merge.request.show.action.description"),
                                                     null) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val controller = e.getData(GitLabMergeRequestsActionKeys.FILES_CONTROLLER)
    val selection = e.getData(GitLabMergeRequestsActionKeys.SELECTED)

    e.presentation.isEnabled = controller != null && selection != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val controller = e.getRequiredData(GitLabMergeRequestsActionKeys.FILES_CONTROLLER)
    val selection = e.getRequiredData(GitLabMergeRequestsActionKeys.SELECTED)

    controller.openTimeline(selection, false)
  }
}