// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.git.actions.GitSingleRefActions
import fleet.util.safeAs
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider
import javax.swing.Icon

internal class GitCreateWorkingTreeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val singleRepository = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)
    if (singleRepository == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val explicitRefFromCtx = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY)
    if (explicitRefFromCtx != null && explicitRefFromCtx !is GitBranch) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
    GitWorkingTreesNewBadgeUtil.addLabelNewIfNeeded(e.presentation)
    e.presentation.icon = computeIcon(e)
    val localBranchFromContext = getBranchFromContext(e, singleRepository, explicitRefFromCtx)
    if (localBranchFromContext == null) {
      e.presentation.text = GitBundle.message("action.Git.CreateNewWorkingTree.text")
      e.presentation.description = GitBundle.message("action.Git.CreateNewWorkingTree.description")
    }
    else {
      val branchName = localBranchFromContext.name
      e.presentation.text = GitBundle.message("action.Git.CreateNewWorkingTree.from.branch.text", branchName)
      e.presentation.description = GitBundle.message("action.Git.CreateNewWorkingTree.from.branch.description", branchName)
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
    val repository = GitWorkingTreesService.getRepoForWorkingTreesSupport(project) ?: return
    val branchFromContext = getBranchFromContext(e, repository)
    GitCreateWorkingTreeService.getInstance().collectDataAndCreateWorkingTree(repository, branchFromContext, e.place)
  }

  private fun getBranchFromContext(
    e: AnActionEvent,
    repository: GitRepository?,
    explicitRefFromCtx: GitReference? = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY),
  ): GitBranch? {
    val ref = when {
      explicitRefFromCtx != null -> explicitRefFromCtx
      e.getData(GitBranchActionsDataKeys.USE_CURRENT_BRANCH) == true -> repository?.currentBranch
      else -> null
    }
    return ref.safeAs<GitBranch>()
  }
}