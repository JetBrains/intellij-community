// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import git4idea.config.GitVcsSettings

/**
 * [GitBranchesTreeShowTagsAction] is updated and executed at backend.
 *
 * The state of the pop-up tree is updated by receiving
 * [com.intellij.vcs.git.rpc.GitRepositoryEvent.TagsHidden] and
 * [com.intellij.vcs.git.rpc.GitRepositoryEvent.TagsLoaded] events.
 */
internal class GitBranchesTreeShowTagsAction : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = e.project?.let(GitVcsSettings::getInstance)?.showTags() ?: true

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    GitVcsSettings.getInstance(project).setShowTags(state)
  }
}
