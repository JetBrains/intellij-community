// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction
import git4idea.actions.GitStash
import git4idea.index.GitStageTracker
import git4idea.repo.GitRepositoryManager
import git4idea.stash.createStashHandler

class GitStashSilentlyAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = e.isFromActionToolbar || GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    e.presentation.isEnabled = project.serviceIfCreated<GitStageTracker>()?.state?.hasChangedRoots() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val roots = GitStageTracker.getInstance(project).state.changedRoots
    GitStash.runStashInBackground(project, roots) { createStashHandler(project, it) }
  }
}