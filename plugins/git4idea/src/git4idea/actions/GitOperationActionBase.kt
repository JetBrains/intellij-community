// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.GitSelectRootDialog
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

abstract class GitOperationActionBase(
  private val repositoryState: Repository.State
) : DumbAwareAction(), GitOngoingOperationAction {
  protected abstract val operationName: @Nls String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = !getAffectedRepositories(e.project).isEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val repository = chooseRepository(project, getAffectedRepositories(project))

    if (repository != null) {
      performInBackground(repository)
    }
  }

  override fun isEnabled(repository: GitRepository): Boolean = repository.state == repositoryState


  private fun getAffectedRepositories(project: Project?): Collection<GitRepository> {
    if (project == null) return emptyList()
    return GitUtil.getRepositoriesInState(project, repositoryState)
  }

  private fun chooseRepository(project: Project, repositories: Collection<GitRepository>): GitRepository? {
    if (repositories.size == 1) return repositories.single()
    return GitSelectRootDialog(project,
                               templatePresentation.text!!,
                               GitBundle.message("operation.action.message", operationName),
                               repositories,
                               null)
      .selectRoot()
  }
}