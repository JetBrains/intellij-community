// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.toActionName
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel

internal class GitLabMergeRequestDiffReviewDiscussionsToggleAction : ActionGroup(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val vm: GitLabMergeRequestDiffViewModel? = e.getData(GitLabMergeRequestDiffViewModel.DATA_KEY)
    e.presentation.isEnabledAndVisible = vm != null
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return DiscussionsViewOption.values().map(::ToggleOptionAction).toTypedArray()
  }

  private class ToggleOptionAction(private val viewOption: DiscussionsViewOption) : ToggleAction(viewOption.toActionName()) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = e.getData(GitLabMergeRequestDiffViewModel.DATA_KEY) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val vm: GitLabMergeRequestDiffViewModel = e.getData(GitLabMergeRequestDiffViewModel.DATA_KEY) ?: return false
      return vm.discussionsViewOption.value == viewOption
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val vm: GitLabMergeRequestDiffViewModel = e.getRequiredData(GitLabMergeRequestDiffViewModel.DATA_KEY)
      vm.setDiscussionsViewOption(viewOption)
    }
  }
}