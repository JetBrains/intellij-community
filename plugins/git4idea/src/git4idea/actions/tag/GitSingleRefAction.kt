// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.tag

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.actions.branch.GitBranchActionsUtil.getAffectedRepositories
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import java.util.function.Supplier

abstract class GitSingleRefAction<T : GitReference>(dynamicText: Supplier<@NlsActions.ActionText String>) : DumbAwareAction(dynamicText) {
  open val disabledForCurrent: Boolean = false

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  final override fun update(e: AnActionEvent) {
    val project = e.project
    val repositories = getAffectedRepositories(e)
    val branches = e.getData(GitBranchActionsDataKeys.BRANCHES)
    val tags = e.getData(GitBranchActionsDataKeys.TAGS)
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(project, repositories, branches, tags)

    DvcsUtil.disableActionIfAnyRepositoryIsFresh(e, repositories, GitBundle.message("action.not.possible.in.fresh.repo.generic"))

    if (e.presentation.isEnabledAndVisible) {
      updateIfEnabledAndVisible(e, project!!, repositories, getRef(branches, tags)!!)
    }
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val branches = e.getData(GitBranchActionsDataKeys.BRANCHES)
    val tags = e.getData(GitBranchActionsDataKeys.TAGS)
    val ref = getRef(branches, tags) ?: return
    val repositories = getAffectedRepositories(e)

    actionPerformed(e, project, repositories, ref)
  }

  abstract fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: T)

  open fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: T) {}

  protected open fun isDisabledForRef(ref: T): Boolean = false

  @Suppress("UNCHECKED_CAST")
  protected open fun getRef(branches: List<GitBranch>?, tags: List<GitTag>?): T? {
    return (tags?.singleOrNull() ?: branches?.singleOrNull()) as? T
  }

  private fun isEnabledAndVisible(project: Project?, repositories: List<GitRepository>?, branches: List<GitBranch>?, tags: List<GitTag>?): Boolean {
    if (project == null) return false
    if (repositories.isNullOrEmpty()) return false
    val ref = getRef(branches, tags) ?: return false

    if (isDisabledForRef(ref)) {
      return false
    }
    if (disabledForCurrent) {
      if (repositories.any { it.currentBranch == ref }) return false
    }

    return true
  }
}

internal abstract class GitSingleTagAction(dynamicText: Supplier<@NlsActions.ActionText String>) : GitSingleRefAction<GitTag>(dynamicText) {
  override fun getRef(branches: List<GitBranch>?, tags: List<GitTag>?): GitTag? = tags?.singleOrNull()
}