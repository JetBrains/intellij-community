// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestReviewViewModel

internal class GitLabMergeRequestHideTestSourcesAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    // The diff of a merge request navigates its change list, so the setting applies there too.
    val available = e.getData(GitLabMergeRequestReviewViewModel.DATA_KEY) != null
    e.presentation.isEnabledAndVisible = available
    if (available) {
      super.update(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    e.project?.service<GitLabMergeRequestsPreferences>()?.hideTestSources ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.project?.service<GitLabMergeRequestsPreferences>()?.hideTestSources = state
  }
}
