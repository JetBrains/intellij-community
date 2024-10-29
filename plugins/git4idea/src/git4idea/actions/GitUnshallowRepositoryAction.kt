// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import git4idea.GitVcs
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

internal class GitUnshallowRepositoryAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val guessedRepo: GitRepository? = project?.let {
      DvcsUtil.guessRepositoryForOperation(it, GitRepositoryManager.getInstance(it), e.dataContext)
    }

    e.presentation.isEnabledAndVisible = guessedRepo != null && guessedRepo.info.isShallow
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val repository =
      DvcsUtil.guessRepositoryForOperation(project, GitRepositoryManager.getInstance(project), e.dataContext)
      ?: return

    val fetcher = GitFetchSupport.fetchSupport(project)
    val remote = fetcher.getDefaultRemoteToFetch(repository) ?: return

    GitVcs.runInBackground(object : Task.Backgroundable(project, GitBundle.message("action.Git.Unshallow.progress.title")) {
      override fun run(indicator: ProgressIndicator) {
        fetcher.fetchUnshallow(repository, remote).showNotificationIfFailed(GitBundle.message("action.Git.Unshallow.failure.title"))
      }
    })
  }

  internal companion object {
    const val ACTION_ID = "Git.Unshallow"
  }
}