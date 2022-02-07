// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
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
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import javax.swing.tree.TreeModel

private typealias PathAndBranch = Pair<List<String>, GitBranch>

class GitBranchesTreePopupStep(private val project: Project, private val repository: GitRepository) : PopupStep<Any> {

  private var finalRunnable: Runnable? = null

  val treeModel: TreeModel = BranchesTreeModel(project, repository)
  val preSelectedPath: List<NodeDescriptor<*>>?
    get() = (treeModel as BranchesTreeModel).getPreSelectionPath()

  override fun getFinalRunnable() = finalRunnable

  override fun hasSubstep(selectedValue: Any?): Boolean {
    val userValue = getNodeValue(selectedValue) ?: return false
    return userValue is GitBranch ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled && userValue.action is ActionGroup)
  }

  fun isSelectable(node: Any?): Boolean {
    val userValue = getNodeValue(node) ?: return false
    return userValue is GitBranch ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }

  override fun onChosen(selectedValue: Any?, finalChoice: Boolean): PopupStep<out Any>? {
    val userValue = getNodeValue(selectedValue) ?: return FINAL_CHOICE

    if (userValue is GitBranch) {
      val actionGroup = ActionManager.getInstance().getAction(BRANCH_ACTION_GROUP) as? ActionGroup ?: DefaultActionGroup()
      val dataContext = object : AsyncDataContext {
        override fun getData(dataId: String): Any? = when {
          CommonDataKeys.PROJECT.`is`(dataId) -> project
          GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> listOf(repository)
          GitBranchActionsUtil.BRANCHES_KEY.`is`(dataId) -> listOf(userValue)
          else -> null
        }
      }
      return createActionStep(actionGroup, dataContext)
    }

    if (userValue is PopupFactoryImpl.ActionItem) {
      if (!userValue.isEnabled) return FINAL_CHOICE
      val action = userValue.action

      val dataContext = object : AsyncDataContext {
        override fun getData(dataId: String): Any? = when {
          CommonDataKeys.PROJECT.`is`(dataId) -> project
          GitBranchActionsUtil.REPOSITORIES_KEY.`is`(dataId) -> listOf(repository)
          else -> null
        }
      }

      if (action is ActionGroup && (!finalChoice || !userValue.isPerformGroup)) {
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

  override fun getSpeedSearchFilter() = SpeedSearchFilter<Any> { node ->
    when (val value = getNodeValue(node)) {
      is GitBranch -> value.name
      else -> value?.toString() ?: ""
    }
  }

  override fun isAutoSelectionEnabled() = false

  companion object {
    private val ACTION_PLACE = ActionPlaces.getPopupPlace("GitBranchesPopup")

    private const val TOP_LEVEL_ACTION_GROUP = "Git.Branches.List"
    private const val BRANCH_ACTION_GROUP = "Git.Branch"

    private fun getNodeValue(node: Any?) = (TreeUtil.getUserObject(node) as? AbstractTreeNode<*>)?.value

    private class BranchesTreeModel(private val project: Project, private val repository: GitRepository) : AbstractTreeModel() {
      private val repositoryNode = Repository()

      private val structureCache = mutableMapOf<Any, List<AbstractTreeNode<*>>>()

      override fun getRoot() = repositoryNode

      override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

      override fun getChildCount(parent: Any?): Int = getChildren(parent).size

      override fun isLeaf(node: Any?): Boolean = (node is Branch) || (node is Action)

      override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

      private fun getChildren(node: Any?): List<AbstractTreeNode<*>> {
        if (node !is AbstractTreeNode<*>) return emptyList()
        return structureCache.getOrPut(node) {
          node.children.toList()
        }
      }

      fun getPreSelectionPath(): List<NodeDescriptor<*>>? {
        val recentBranchName = GitVcsSettings.getInstance(project).recentBranchesByRepository[repository.root.path]
        if (recentBranchName != null) {
          return repository.branches.localBranches.find { it.name == recentBranchName }?.let(::createTreePathFor)
        }

        val currentBranch = repository.currentBranch
        if (currentBranch != null) {
          return createTreePathFor(currentBranch)
        }

        return null
      }

      private fun createTreePathFor(branch: GitBranch): List<NodeDescriptor<*>> {
        val root = root as NodeDescriptor<*>
        val group = (if (branch.isRemote) RemoteBranches() else LocalBranches())

        val path = mutableListOf<NodeDescriptor<*>>().apply {
          add(root)
          add(group)
        }
        val nameParts = branch.name.split('/')
        nameParts.subList(0, nameParts.size - 1).forEach {
          val folder = Folder(it) { emptyList() }
          path.add(folder)
        }
        path.add(Branch(branch, nameParts.last()))
        return path
      }

      private inner class Repository : Node<GitRepository>(project, repository) {

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

          result.add(LocalBranches())
          result.add(RemoteBranches())

          return result
        }

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value.toString()
        }
      }

      private inner class Action(action: PopupFactoryImpl.ActionItem)
        : Node<PopupFactoryImpl.ActionItem>(project, action) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value.text
          //todo: renderer has to be offset to the left somehow
          //presentation.setIcon(value.getIcon(false))
          presentation.tooltip = value.tooltip
        }
      }

      private inner class LocalBranches : Node<String>(project, "Local") {

        override fun getChildren() = groupBranches(repository.branches.localBranches.map { it.name.split('/') to it })

        override fun update(presentation: PresentationData) {
          presentation.presentableText = GitBundle.message("group.Git.Local.Branch.title")
        }
      }

      private inner class RemoteBranches : Node<String>(project, "Remote") {

        override fun getChildren() = groupBranches(repository.branches.remoteBranches.map { it.name.split('/') to it })

        override fun update(presentation: PresentationData) {
          presentation.presentableText = GitBundle.message("group.Git.Remote.Branch.title")
        }
      }

      private inner class Folder(folderName: String, private val branchesSupplier: () -> List<PathAndBranch>)
        : Node<String>(project, folderName) {

        override fun getChildren() = groupBranches(branchesSupplier())

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value
          presentation.setIcon(PlatformIcons.FOLDER_ICON)
        }
      }

      private inner class Branch(branch: GitBranch, private val displayName: String)
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

      private sealed class Node<T : Any>(project: Project, value: T)
        : AbstractTreeNode<T>(project, value) {

        override fun shouldPostprocess() = false
      }

      private fun groupBranches(branches: List<PathAndBranch>): List<Node<*>> {
        val result = mutableListOf<Node<*>>()
        val groupsByPrefix = mutableMapOf<String, List<PathAndBranch>>()

        for ((pathParts, branch) in branches) {
          if (pathParts.size <= 1) {
            result.add(Branch(branch, pathParts.first()))
            continue
          }

          groupsByPrefix.compute(pathParts.first()) { _, currentList ->
            (currentList ?: mutableListOf()) + (pathParts to branch)
          }
        }

        for ((prefix, branchesWithPaths) in groupsByPrefix) {
          result.add(Folder(prefix) {
            branchesWithPaths.map { (path, branch) -> path.tail() to branch }
          })
        }

        return result
      }
    }
  }
}