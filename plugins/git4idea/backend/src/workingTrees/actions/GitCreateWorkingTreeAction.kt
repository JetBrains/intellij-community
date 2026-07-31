// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.git.actions.GitSingleRefActions
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitCreateWorkingTreeService
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.GitWorktreeSupportStatus
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import javax.swing.Icon

internal class GitCreateWorkingTreeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    if (status is GitWorktreeSupportStatus.Unsupported) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val explicitRefFromCtx = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY)
    if (explicitRefFromCtx != null && explicitRefFromCtx !is GitBranch && explicitRefFromCtx !is GitTag) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    GitWorkingTreesNewBadgeUtil.addLabelNewIfNeeded(e.presentation)
    e.presentation.icon = computeIcon(e)
    val contextRepository = resolveContextRepository(e, status)
    val localBranchFromContext = getRefFromContext(e, contextRepository, explicitRefFromCtx)
    if (localBranchFromContext == null) {
      e.presentation.text = GitBundle.message("action.Git.CreateNewWorkingTree.text")
      e.presentation.description = GitBundle.message("action.Git.CreateNewWorkingTree.description")
    }
    else {
      val refName = localBranchFromContext.name
      val text = GitBundle.message("action.Git.CreateNewWorkingTree.from.branch.text", refName)
      e.presentation.setText(text, false)

      val description = if (localBranchFromContext is GitTag) {
        GitBundle.message("action.Git.CreateNewWorkingTree.from.tag.description", refName)
      }
      else {
        GitBundle.message("action.Git.CreateNewWorkingTree.from.branch.description", refName)
      }
      e.presentation.description = description
    }
  }

  private fun computeIcon(e: AnActionEvent): Icon? {
    return if (e.place == GitWorkingTreesContentProvider.GIT_WORKING_TREE_TOOLWINDOW_TAB_TOOLBAR) {
      AllIcons.General.Add
    }
    else {
      null
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val project = e.project ?: return
    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    val contextRepository = resolveContextRepository(e, status)
    val refFromContext = getRefFromContext(e, contextRepository)

    // When creating from a specific ref, the ref belongs to a specific repository, so no switching is offered.
    // Otherwise the dialog lets the user pick/switch the target repository among all worktree-capable ones.
    val candidates = if (refFromContext != null && contextRepository != null) listOf(contextRepository)
        else GitWorkingTreesService.worktreeCapableRepositories(project)
    val initialRepository = contextRepository ?: candidates.firstOrNull() ?: return

    GitCreateWorkingTreeService.getInstance()
      .collectDataAndCreateWorkingTree(initialRepository, refFromContext, e.place, candidates)
  }

  private fun resolveContextRepository(e: AnActionEvent, status: GitWorktreeSupportStatus): GitRepository? =
    e.getData(GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY)
    ?: e.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY)
    ?: (status as? GitWorktreeSupportStatus.SingleRepository)?.repository

  private fun getRefFromContext(
    e: AnActionEvent,
    repository: GitRepository?,
    explicitRefFromCtx: GitReference? = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY),
  ): GitReference? {
    val ref = when {
      explicitRefFromCtx != null -> explicitRefFromCtx
      e.getData(GitBranchActionsDataKeys.USE_CURRENT_BRANCH) == true -> repository?.currentBranch
      else -> null
    }
    return if (ref is GitBranch || ref is GitTag) ref else null
  }
}
