// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel

class GitLabMergeRequestShowDiffActionProvider : AnActionExtensionProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isActive(e: AnActionEvent): Boolean = e.getData(GitLabMergeRequestChangeListViewModel.DATA_KEY) != null

  override fun update(e: AnActionEvent) {
    val vm = e.getData(GitLabMergeRequestChangeListViewModel.DATA_KEY)
    e.presentation.isEnabled = vm != null && vm.changesSelection.value != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getRequiredData(GitLabMergeRequestChangeListViewModel.DATA_KEY)
    vm.showDiff()
  }
}
