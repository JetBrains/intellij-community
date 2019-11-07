// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.diverged
import com.intellij.dvcs.repo.Repository
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitUtil
import git4idea.branch.GitBrancher
import git4idea.config.GitVcsSettings
import git4idea.isRemoteBranchProtected
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupActions
import javax.swing.Icon

internal object BranchesDashboardActions {

  class BranchesTreeActionGroup(private val project: Project, private val tree: FilteringBranchesTree) : ActionGroup(), DumbAware {
    override fun update(e: AnActionEvent) {
      val enabledAndVisible = tree.getSelectedBranches().isNotEmpty()
      e.presentation.isEnabledAndVisible = enabledAndVisible
      isPopup = enabledAndVisible
    }

    override fun hideIfNoVisibleChildren() = true

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
      BranchActionsBuilder(project, tree).build()?.getChildren(e) ?: AnAction.EMPTY_ARRAY
  }

  class MultipleLocalBranchActions : ActionGroup(), DumbAware {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(DeleteBranchAction())
  }

  class CurrentBranchActions(project: Project,
                             repositories: List<GitRepository>,
                             branchName: String,
                             currentRepository: GitRepository)
    : GitBranchPopupActions.CurrentBranchActions(project, repositories, branchName, currentRepository) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val children = arrayListOf<AnAction>(NewBranchAction(), *super.getChildren(e))
      if (myRepositories.diverged()) {
        children.add(1, CheckoutAction(myProject, myRepositories, myBranchName))
      }
      return children.toTypedArray()
    }
  }

  class BranchActionsBuilder(private val project: Project, private val tree: FilteringBranchesTree) {
    fun build(): ActionGroup? {
      val selectedBranches = tree.getSelectedBranches()
      val multipleBranchSelection = selectedBranches.size > 1
      val guessRepo = DvcsUtil.guessCurrentRepositoryQuick(project, GitUtil.getRepositoryManager(project),
                                                           GitVcsSettings.getInstance(project).recentRootPath) ?: return null
      if (multipleBranchSelection) {
        return MultipleLocalBranchActions()
      }
      else {
        val branchInfo = selectedBranches.singleOrNull() ?: return null
        return when {
          branchInfo.isCurrent -> CurrentBranchActions(project, branchInfo.repositories, branchInfo.branchName, guessRepo)
          branchInfo.isLocal -> GitBranchPopupActions.LocalBranchActions(project, branchInfo.repositories, branchInfo.branchName, guessRepo)
          else -> GitBranchPopupActions.RemoteBranchActions(project, branchInfo.repositories, branchInfo.branchName, guessRepo)
        }
      }
    }
  }

  class NewBranchAction : BranchesActionBase(com.intellij.dvcs.ui.NewBranchAction.text,
                                             com.intellij.dvcs.ui.NewBranchAction.description,
                                             com.intellij.dvcs.ui.NewBranchAction.icon) {

    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      if (branches.size > 1) {
        e.presentation.isEnabled = false
        e.presentation.description = "Select only one branch to proceed create a new branch"
        return
      }

      e.presentation.description = com.intellij.dvcs.ui.NewBranchAction.description
      val repositories = branches.flatMap(BranchInfo::repositories).distinct()
      com.intellij.dvcs.ui.NewBranchAction.checkIfAnyRepositoryIsFresh(e, repositories)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      val repositories = branches.flatMap(BranchInfo::repositories).distinct()
      GitBranchPopupActions.GitNewBranchAction(project, repositories).actionPerformed(e)
    }
  }

  class DeleteBranchAction : BranchesActionBase(icon = AllIcons.Actions.GC) {
    override fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {
      e.presentation.text = "Delete ${StringUtil.pluralize("Branch", branches.size)}"
      val disabled = branches.any { it.isCurrent || (!it.isLocal && isRemoteBranchProtected(it.repositories, it.branchName)) }
      e.presentation.isEnabled = !disabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      delete(project, branches)
    }

    private fun delete(project: Project, branches: Collection<BranchInfo>) {
      val gitBrancher = GitBrancher.getInstance(project)
      val (localBranches, remoteBranches) = branches.partition { it.isLocal && !it.isCurrent }
      with(gitBrancher) {
        val localBranchNames = localBranches.map(BranchInfo::branchName)
        val repositories = localBranches.flatMap(BranchInfo::repositories).distinct()
        val deleteRemoteBranches = {
          deleteRemoteBranches(remoteBranches.map(BranchInfo::branchName), remoteBranches.flatMap(BranchInfo::repositories).distinct())
        }
        if (localBranchNames.isNotEmpty()) { //delete local (possible tracked) branches first if any
          deleteBranches(localBranchNames, repositories, deleteRemoteBranches)
        }
        else {
          deleteRemoteBranches()
        }
      }
    }
  }

  class ShowBranchDiffAction : BranchesActionBase("Compare with Current", null, AllIcons.Actions.Diff) {
    override fun actionPerformed(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)!!
      val project = e.project!!
      val gitBrancher = GitBrancher.getInstance(project)

      for (branch in branches) {
        gitBrancher.compare(branch.branchName, branch.repositories)
      }
    }
  }

  class ShowMyBranchesAction(private val uiController: BranchesDashboardController)
    : ToggleAction("Show My Branches", null, AllIcons.Actions.Find), DumbAware {

    override fun isSelected(e: AnActionEvent) = uiController.showOnlyMy

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      uiController.showOnlyMy = state
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      val project = e.getData(CommonDataKeys.PROJECT)
      if (project == null) {
        e.presentation.isEnabled = false
        return
      }
      val log = VcsProjectLog.getInstance(project)
      val supportsIndexing = log.dataManager?.logProviders?.all {
        VcsLogProperties.SUPPORTS_INDEXING.getOrDefault(it.value)
      } ?: false

      val isGraphReady = log.dataManager?.dataPack?.isFull ?: false

      val allRootsIndexed = GitRepositoryManager.getInstance(project).repositories.all {
        log.dataManager?.index?.isIndexed(it.root) ?: false
      }

      e.presentation.isEnabled = supportsIndexing && isGraphReady && allRootsIndexed
      e.presentation.description = if (!supportsIndexing) {
        "Some of repositories doesn't support indexing."
      }
      else if (!allRootsIndexed) {
        "Not all repositories are indexed."
      }
      else if (!isGraphReady) {
        "The log is not ready yet, please wait a bit."
      }
      else {
        "A branch is 'My' if all exclusive commits of this branch are made by 'me', i.e. by current Git author."
      }
    }
  }

  abstract class BranchesActionBase(text: String? = null, description: String? = null, icon: Icon? = null) :
    DumbAwareAction(text, description, icon) {

    open fun update(e: AnActionEvent, project: Project, branches: Collection<BranchInfo>) {}

    override fun update(e: AnActionEvent) {
      val branches = e.getData(GIT_BRANCHES)
      val project = e.project
      val enabled = project != null && branches != null && branches.isNotEmpty()
      e.presentation.isEnabled = enabled
      if (enabled) {
        update(e, project!!, branches!!)
      }
    }
  }

  object CheckoutLocalBranchOnDoubleClickHandler {
    fun install(project: Project, treeComponent: BranchesTreeComponent) {
      treeComponent.doubleClickHandler = { clickedNode -> doCheckout(clickedNode, project) }
    }

    private fun doCheckout(clickedNode: BranchTreeNode, project: Project) {
      val branchInfo = clickedNode.getNodeDescriptor().branchInfo ?: return
      if (branchInfo.isLocal) {
        GitBranchPopupActions.LocalBranchActions.CheckoutAction
          .checkoutBranch(project, branchInfo.repositories, branchInfo.branchName)
      }
      else {
        GitBranchPopupActions.RemoteBranchActions.CheckoutRemoteBranchAction
          .checkoutRemoteBranch(project, branchInfo.repositories, branchInfo.branchName)
      }
    }
  }

  object RenameLocalBranchOnF2KeyPressHandler {
    fun install(project: Project, treeComponent: BranchesTreeComponent) {
      treeComponent.keyPressHandler = { clickedNode -> doRename(clickedNode, project) }
    }

    private fun doRename(clickedNode: BranchTreeNode, project: Project) {
      val branchInfo = clickedNode.getNodeDescriptor().branchInfo ?: return
      if (!branchInfo.isLocal || branchInfo.repositories.any(Repository::isFresh)) return

      GitBranchPopupActions.LocalBranchActions.RenameBranchAction.rename(project, branchInfo.repositories, branchInfo.branchName)
    }
  }
}
