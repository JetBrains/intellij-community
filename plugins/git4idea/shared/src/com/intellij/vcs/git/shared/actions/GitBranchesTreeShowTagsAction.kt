// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.platform.project.projectId
import com.intellij.vcs.git.shared.rpc.GitUiSettingsApi
import git4idea.GitDisposable
import git4idea.config.GitVcsSettings
import kotlinx.coroutines.launch

internal class GitBranchesTreeShowTagsAction : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = e.project?.let(GitVcsSettings::getInstance)?.showTags() ?: true

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return

    GitDisposable.getInstance(project).childScope("Git toggle show tags").launch {
      GitUiSettingsApi.getInstance().setShowTags(project.projectId(), state)
    }
  }
}
