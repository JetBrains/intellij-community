// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.AsyncDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.tail
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import icons.DvcsImplIcons

private typealias PathAndBranch = Pair<List<String>, GitBranch>

class GitBranchesTreePopupStep(private val project: Project, private val repository: GitRepository) : PopupStep<Any> {

  private var finalRunnable: Runnable? = null

  val structure: AbstractTreeStructureBase = BranchesTreeStructure(project, repository)

  override fun getFinalRunnable() = finalRunnable

  override fun hasSubstep(selectedValue: Any?) =
    selectedValue is BranchesTreeStructure.Branch ||
    (selectedValue is BranchesTreeStructure.Action && selectedValue.value.isEnabled && selectedValue.value.action is ActionGroup)

  fun isSelectable(nodeData: Any?) =
    nodeData is BranchesTreeStructure.Branch || nodeData is BranchesTreeStructure.Action

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<out Any>? {
    if (selectedValue is BranchesTreeStructure.Branch) {
      val actionGroup = ActionManager.getInstance().getAction(BRANCH_ACTION_GROUP) as? ActionGroup ?: DefaultActionGroup()
      val dataContext = object : AsyncDataContext {
        override fun getData(dataId: String): Any? = when {
          CommonDataKeys.PROJECT.`is`(dataId) -> project
          GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> listOf(repository)
          GitBranchActionsUtil.BRANCHES_KEY.`is`(dataId) -> listOf(selectedValue.value)
          else -> null
        }
      }
      return createActionStep(actionGroup, dataContext)
    }

    if (selectedValue is BranchesTreeStructure.Action) {
      val item = selectedValue.value
      if (!item.isEnabled) return FINAL_CHOICE
      val action = item.action

      val dataContext = object : AsyncDataContext {
        override fun getData(dataId: String): Any? = when {
          CommonDataKeys.PROJECT.`is`(dataId) -> project
          GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> listOf(repository)
          else -> null
        }
      }

      if (action is ActionGroup && (!finalChoice || !item.isPerformGroup)) {
        return createActionStep(action, dataContext)
      }
      else {
        finalRunnable = Runnable {
          ActionPopupStep.performAction({ dataContext }, ACTION_PLACE, action, 0, null)
        }
      }
    }

    return FINAL_CHOICE
  }

  private fun createActionStep(actionGroup: ActionGroup, dataContext: DataContext): ListPopupStep<*> {
    return JBPopupFactory.getInstance()
      .createActionsStep(actionGroup, dataContext, ACTION_PLACE, false, true, null, null, true, 0, false)
  }

  override fun getTitle() =
    DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", repository.vcs.displayName, DvcsUtil.getShortRepositoryName(repository))

  override fun canceled() {}

  override fun isMnemonicsNavigationEnabled() = false

  override fun getMnemonicNavigationFilter() = null

  override fun isSpeedSearchEnabled() = true

  override fun getSpeedSearchFilter() = SpeedSearchFilter<Any> { value -> value.toString() }

  override fun isAutoSelectionEnabled() = false

  companion object {
    private val ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup")

    private const val TOP_LEVEL_ACTION_GROUP = "Git.Branches.List"
    private const val BRANCH_ACTION_GROUP = "Git.Branch"

    private class BranchesTreeStructure(private val project: Project, private val repository: GitRepository)
      : AbstractTreeStructureBase(project) {

      override fun getRootElement() = Repository(repository)
      override fun commit() = Unit
      override fun hasSomethingToCommit() = false
      override fun getProviders(): List<TreeStructureProvider>? = null

      inner class Repository(repository: GitRepository)
        : Node<GitRepository>(project, repository) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> {
          val result = mutableListOf<Node<*>>()

          val actionGroup = ActionManager.getInstance().getAction(TOP_LEVEL_ACTION_GROUP) as? ActionGroup
          if (actionGroup != null) {
            val dataContext = object : AsyncDataContext {
              override fun getData(dataId: String): Any? = when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> listOf(repository)
                else -> null
              }
            }
            ActionPopupStep.createActionItems(actionGroup, dataContext, false, false, false, false, ACTION_PLACE, null).forEach {
              result.add(Action(it))
            }
          }

          val branchesCollection = value.branches


          result.add(makeTopLevelGroup(GitBundle.message("group.Git.Local.Branch.title"), branchesCollection.localBranches))
          result.add(makeTopLevelGroup(GitBundle.message("group.Git.Remote.Branch.title"), branchesCollection.remoteBranches))

          return result
        }

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value.toString()
        }

        private fun makeTopLevelGroup(name: String, branches: Collection<GitBranch>): Group {
          return Group(project, name, { branches.map { it.name.split('/') to it } }, false)
        }
      }

      inner class Action(action: PopupFactoryImpl.ActionItem)
        : Node<PopupFactoryImpl.ActionItem>(project, action) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value.text
          //todo: renderer has to be offset to the left somehow
          //presentation.setIcon(value.getIcon(false))
          presentation.tooltip = value.tooltip
        }
      }

      inner class Group(project: Project,
                        private val groupName: String,
                        private val branchesSupplier: () -> List<PathAndBranch>,
                        private val isFolder: Boolean = true)
        : Node<String>(project, groupName) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> {
          val branches = branchesSupplier()
          val result = mutableListOf<Node<*>>()
          val groupsByPrefix = mutableMapOf<String, List<PathAndBranch>>()

          for ((pathParts, branch) in branches) {
            if (pathParts.size <= 1) {
              result.add(Branch(project, branch, pathParts.first()))
              continue
            }

            groupsByPrefix.compute(pathParts.first()) { _, currentList ->
              (currentList ?: mutableListOf()) + (pathParts to branch)
            }
          }

          for ((prefix, branchesWithPaths) in groupsByPrefix) {
            result.add(Group(project, prefix, {
              branchesWithPaths.map { (path, branch) -> path.tail() to branch }
            }))
          }

          return result
        }

        override fun update(presentation: PresentationData) {
          presentation.presentableText = groupName
          presentation.setIcon(if (isFolder) PlatformIcons.FOLDER_ICON else null)
        }
      }

      inner class Branch(project: Project, branch: GitBranch, private val displayName: String)
        : Node<GitBranch>(project, branch) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

        override fun update(presentation: PresentationData) {
          val isCurrent = repository.currentBranch == value
          val isFavorite = project.service<GitBranchManager>().isFavorite(GitBranchType.of(value), repository, value.name)

          presentation.presentableText = displayName
          presentation.setIcon(when {
                                 isCurrent && isFavorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
                                 isCurrent -> DvcsImplIcons.CurrentBranchLabel
                                 isFavorite -> AllIcons.Nodes.Favorite
                                 else -> AllIcons.Vcs.BranchNode
                               })
        }
      }

      sealed class Node<T : Any>(project: Project, value: T)
        : AbstractTreeNode<T>(project, value) {

        override fun shouldPostprocess() = false
      }
    }
  }
}