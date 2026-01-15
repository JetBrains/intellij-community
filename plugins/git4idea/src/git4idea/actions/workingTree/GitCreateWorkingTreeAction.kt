// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.vcs.git.actions.GitSingleRefActions
import fleet.util.safeAs
import git4idea.GitBranch
import git4idea.GitNotificationIdsHolder
import git4idea.GitOperationsCollector
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    e.presentation.isEnabledAndVisible = true
    e.presentation.icon = computeIcon(e)
    val localBranchFromContext = getBranchFromContext(e, singleRepository)
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
    val project = e.project ?: return
    val repository = GitWorkingTreesService.getRepoForWorkingTreesSupport(project) ?: return
    val branchFromContext = getBranchFromContext(e, repository)
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(e, branchFromContext)
    service<GitWorkingTreeAppService>().coroutineScope.launch(Dispatchers.Default) {
      val preDialogData = readAction {
        val initialParentPath = computeInitialParentPath(project, repository)
        GitWorkingTreePreDialogData(project, repository, ideActivity, branchFromContext, initialParentPath)
      }

      withContext(Dispatchers.UiWithModelAccess) {
        val dialog = GitWorkingTreeDialog(preDialogData)
        if (dialog.showAndGet()) {
          val workingTreeData = dialog.getWorkTreeData()
          withContext(Dispatchers.Default) {
            doCreateWorkingTree(preDialogData.project, preDialogData.repository, preDialogData.ideActivity, workingTreeData)
          }
        }
      }
    }
  }

  private fun getBranchFromContext(e: AnActionEvent, repository: GitRepository?): GitBranch? {
    val explicitRefFromCtx = e.getData(GitSingleRefActions.SELECTED_REF_DATA_KEY)
    val ref = when {
      explicitRefFromCtx != null -> explicitRefFromCtx
      e.getData(GitBranchActionsDataKeys.USE_CURRENT_BRANCH) == true -> repository?.currentBranch
      else -> null
    }
    return ref.safeAs<GitBranch>()
  }

  /**
   * Searches for a directory that doesn't lie under any of roots of the [project].
   */
  @RequiresReadLock
  private fun computeInitialParentPath(project: Project, repository: GitRepository): VirtualFile? {
    val fromProject = project.guessProjectDir()?.parent
    var root: VirtualFile? = fromProject ?: repository.root.parent
    val index = ProjectFileIndex.getInstance(project)
    while (root != null && index.isInProjectOrExcluded(root)) {
      root = root.parent
    }
    return root
  }

  private suspend fun doCreateWorkingTree(
    project: Project,
    repository: GitRepository,
    ideActivity: StructuredIdeActivity,
    workingTreeData: GitWorkingTreeDialogData,
  ) {
    GitOperationsCollector.logWorktreeCreationDialogExitedWithOk(ideActivity, workingTreeData)

    val path = workingTreeData.workingTreePath.path
    val destinationValidation = CloneDvcsValidationUtils.createDestination(path)
    if (destinationValidation != null) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKTREE_COULD_NOT_CREATE_TARGET_DIR,
                                                   GitBundle.message("notification.title.worktree.creation.failed"),
                                                   destinationValidation.message,
                                                   true)
      return
    }

    val result = GitWorkingTreesService.getInstance(project).createWorkingTree(repository, workingTreeData)

    if (!result.success) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKTREE_ADD_FAILED,
                                                   GitBundle.message("notification.title.worktree.creation.failed"),
                                                   result.errorOutputAsHtmlString,
                                                   true)
      return
    }

    val worktreeProject = ProjectUtil.openOrImport(workingTreeData.workingTreePath.path, null, false)
    if (worktreeProject != null) {
      GitOperationsCollector.logWorktreeProjectOpenedAfterCreation(ideActivity)
    }
  }
}

@Service(Service.Level.APP)
private class GitWorkingTreeAppService(val coroutineScope: CoroutineScope)