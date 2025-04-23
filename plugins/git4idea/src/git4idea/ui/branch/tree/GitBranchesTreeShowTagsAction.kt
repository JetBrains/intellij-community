// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle

internal class GitBranchesTreeShowTagsAction :
  ToggleAction(GitBundle.messagePointer("git.branches.popup.show.tags.name")), DumbAware {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean =
    e.project?.let(GitVcsSettings::getInstance)?.showTags() ?: true

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    GitVcsSettings.getInstance(project).setShowTags(state)
  }

  companion object {
    fun isSelected(project: Project?): Boolean =
      project != null && project.let(GitVcsSettings::getInstance).showTags()
  }
}
