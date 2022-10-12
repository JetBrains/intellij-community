// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsUtil.getAffectedRepositories
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import java.util.function.Supplier

abstract class GitSingleBranchAction(dynamicText: Supplier<@NlsActions.ActionText String>) : DumbAwareAction(dynamicText) {

  constructor() : this(Presentation.NULL_STRING)

  open val disabledForLocal: Boolean = false
  open val disabledForRemote: Boolean = false
  open val disabledForCurrent: Boolean = false

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  final override fun update(e: AnActionEvent) {
    val project = e.project
    val repositories = getAffectedRepositories(e)
    val branches = e.getData(GitBranchActionsUtil.BRANCHES_KEY)
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(project, repositories, branches)

    DvcsUtil.disableActionIfAnyRepositoryIsFresh(e, repositories, GitBundle.message("action.not.possible.in.fresh.repo.generic"))

    if (e.presentation.isEnabledAndVisible) {
      updateIfEnabledAndVisible(e, project!!, repositories, branches!!.single())
    }
  }

  private fun isEnabledAndVisible(project: Project?, repositories: List<GitRepository>?, branches: List<GitBranch>?): Boolean {
    if (project == null) return false
    if (repositories.isNullOrEmpty()) return false
    if (branches.isNullOrEmpty()) return false
    if (branches.size != 1) return false

    val branch = branches.single()
    if (disabledForLocal) {
      if (!branch.isRemote) return false
    }

    if (disabledForRemote) {
      if (branch.isRemote) return false
    }

    if (disabledForCurrent) {
      if (repositories.any { it.currentBranch == branch }) return false
    }

    return true
  }

  open fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {}

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val repositories = getAffectedRepositories(e)
    val branch = e.getRequiredData(GitBranchActionsUtil.BRANCHES_KEY).single()

    actionPerformed(e, project, repositories, branch)
  }

  abstract fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch)
}
