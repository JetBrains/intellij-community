// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.ref

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ReflectionUtil
import com.intellij.vcs.git.actions.GitSingleRefActions
import com.intellij.vcs.git.actions.branch.GitBranchActionToBeWrapped
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.GitWorkingTree
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import java.util.function.Supplier
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

abstract class GitSingleRefAction<T : GitReference>(
  dynamicText: Supplier<@NlsActions.ActionText String>,
) : GitBranchActionToBeWrapped, DumbAwareAction(dynamicText) {

  @Suppress("UNCHECKED_CAST")
  protected open val refClass: Class<T> = GitReference::class.java as Class<T>

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

    return if (refClass.isInstance(ref)) refClass.cast(ref) else null
  }

  private fun isEnabledAndVisible(project: Project?, repositories: List<GitRepository>?, ref: T?): Boolean =
    if (project == null || repositories.isNullOrEmpty() || ref == null) false
    else isEnabledForRef(ref, repositories)

  companion object {
    internal fun isCurrentRefInAnyRepo(ref: GitReference, repositories: List<GitRepository>) = repositories.any {
      when (ref) {
        is GitLocalBranch -> it.currentBranch == ref
        is GitTag -> it.state == Repository.State.DETACHED && GitRefUtil.getCurrentReference(it) == ref
        else -> false
      }
    }

    /**
     * @return true if there is at least one repository having a working tree (excluding the repository itself) with the given reference checked out.
     */
    fun isCurrentRefInAnyOtherWorkingTree(ref: GitReference, repositories: List<GitRepository>): Boolean {
      // Only local branches are checked for working trees.
      // Tags are immutable and therefore may be easily used in multiple working trees simultaneously.
      return if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled() || ref !is GitLocalBranch) false
      else repositories.any { repository -> getWorkingTreeWithRef(ref, repository, true) != null }
    }

    /**
     * See [com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil.getWorkingTreeWithRef]
     */
    fun getWorkingTreeWithRef(reference: GitReference, repository: GitRepository, skipCurrentWorkingTree: Boolean): GitWorkingTree? {
      return GitWorkingTreesUtil.getWorkingTreeWithRef(reference, repository, skipCurrentWorkingTree) {
        repository.workingTreeHolder.getWorkingTrees()
      }
    }

    internal fun getWorkingTreeWithRef(reference: GitReference, repositories: List<GitRepository>, skipCurrentWorkingTree: Boolean): GitWorkingTree? {
      val repository = repositories.singleOrNull() ?: return null
      return getWorkingTreeWithRef(reference, repository, skipCurrentWorkingTree)
    }
  }
}

