// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.ref

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.git.actions.GitSingleRefActions
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import java.util.function.Supplier
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

abstract class GitSingleRefAction<T : GitReference>(dynamicText: Supplier<@NlsActions.ActionText String>) : DumbAwareAction(dynamicText) {
  @Suppress("UNCHECKED_CAST")
  protected open val refClass: KClass<T> = GitReference::class as KClass<T>

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  final override fun update(e: AnActionEvent) {
    val project = e.project
    val repositories = GitBranchActionsUtil.getAffectedRepositories(e)
    val ref = getRef(e, repositories)
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(project, repositories, ref)

    DvcsUtil.disableActionIfAnyRepositoryIsFresh(e, repositories, GitBundle.message("action.not.possible.in.fresh.repo.generic"))

    if (e.presentation.isEnabledAndVisible) {
      updateIfEnabledAndVisible(e, project!!, repositories, ref!!)
    }
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val repositories = GitBranchActionsUtil.getAffectedRepositories(e)
    val ref = getRef(e, repositories) ?: return

    actionPerformed(e, project, repositories, ref)
  }

  abstract fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: T)

  open fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: T) {}

  protected open fun isEnabledForRef(ref: T, repositories: List<GitRepository>): Boolean = true

  private fun getRef(e: AnActionEvent, repositories: List<GitRepository>): T? {
    val explicitRefFromCtx = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY)
    val ref = when {
      explicitRefFromCtx != null -> explicitRefFromCtx
      e.getData(GitBranchActionsDataKeys.USE_CURRENT_BRANCH) == true -> repositories.singleOrNull()?.currentBranch
      else -> null
    }

    return refClass.safeCast(ref)
  }

  private fun isEnabledAndVisible(project: Project?, repositories: List<GitRepository>?, ref: T?): Boolean =
    if (project == null || repositories.isNullOrEmpty() || ref == null) false
    else isEnabledForRef(ref, repositories)

  companion object {
    internal fun isCurrentRefInAnyRepo(ref: GitReference, repositories: List<GitRepository>) = repositories.any {
      when(ref) {
        is GitLocalBranch -> it.currentBranch == ref
        is GitTag -> it.state == Repository.State.DETACHED && GitRefUtil.getCurrentReference(it) == ref
        else -> false
      }
    }
  }
}
