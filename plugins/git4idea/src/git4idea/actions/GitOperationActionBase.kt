// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitSelectRootDialog
import git4idea.repo.GitRepository
import git4idea.ui.toolbar.GIT_MERGE_REBASE_WIDGET_PLACE
import org.jetbrains.annotations.Nls
import javax.swing.Icon

abstract class GitOperationActionBase (
  private val repositoryState: Repository.State
) : DumbAwareAction() {
  protected abstract val operationName: @Nls String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = !getAffectedRepositories(e.project).isEmpty()
    if (e.presentation.isVisible && e.place == GIT_MERGE_REBASE_WIDGET_PLACE) {
      e.presentation.icon = getMainToolbarIcon()
    }
  }

  open fun getMainToolbarIcon(): Icon? {
    return null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val affectedRepositories = getAffectedRepositories(project)
    if (affectedRepositories.isEmpty()) return

    val defaultRepo = GitBranchUtil.guessRepositoryForOperation(project, e.dataContext)
    val repository = chooseRepository(project, affectedRepositories, defaultRepo)

    if (repository != null) {
      performInBackground(repository)
    }
  }

  abstract fun performInBackground(repository: GitRepository)

  private fun getAffectedRepositories(project: Project?): Collection<GitRepository> {
    if (project == null) return emptyList()
    return GitUtil.getRepositoriesInStates(project, repositoryState)
  }

  private fun chooseRepository(project: Project, repositories: Collection<GitRepository>, defaultRepo: GitRepository?): GitRepository? {
    if (repositories.size == 1) return repositories.single()
    return GitSelectRootDialog(project,
                               templatePresentation.text!!,
                               GitBundle.message("operation.action.message", operationName),
                               repositories,
                               defaultRepo)
      .selectRoot()
  }
}