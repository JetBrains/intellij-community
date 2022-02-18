// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import git4idea.actions.GitStash
import git4idea.index.GitStageTracker
import git4idea.index.isStagingAreaAvailable
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.createStashHandler

class GitStashSilentlyAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val repositories = GitRepositoryManager.getInstance(project).repositories
    e.presentation.isVisible = e.isFromActionToolbar || repositories.isNotEmpty()
    e.presentation.isEnabled = changedRoots(project, repositories).isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val repositories = GitRepositoryManager.getInstance(project).repositories
    GitStash.runStashInBackground(project, changedRoots(project, repositories)) {
      createStashHandler(project, it)
    }
  }

  private fun changedRoots(project: Project, repositories: Collection<GitRepository>): Collection<VirtualFile> {
    if (isStagingAreaAvailable(project)) {
      val gitStageTracker = project.serviceIfCreated<GitStageTracker>() ?: return emptyList()
      return gitStageTracker.state.changedRoots
    }
    return repositories.filter { repository ->
      ChangeListManager.getInstance(project).haveChangesUnder(repository.root) != ThreeState.NO
    }.map { it.root }
  }
}