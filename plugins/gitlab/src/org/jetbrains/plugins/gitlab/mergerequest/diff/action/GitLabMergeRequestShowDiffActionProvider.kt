// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.diff.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel

class GitLabMergeRequestShowDiffActionProvider : AnActionExtensionProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isActive(e: AnActionEvent): Boolean = e.getData(GitLabMergeRequestChangesViewModel.DATA_KEY) != null

  override fun update(e: AnActionEvent) {
    val vm = e.getData(GitLabMergeRequestChangesViewModel.DATA_KEY)
    e.presentation.isEnabled = vm != null && !vm.userChangesSelection.value.isEmpty
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getRequiredData(GitLabMergeRequestChangesViewModel.DATA_KEY)
    vm.showDiff()
  }
}
