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
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.PlatformIcons
import com.intellij.util.containers.tail
import com.intellij.util.text.Matcher
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import java.util.IdentityHashMap
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

private typealias PathAndBranch = Pair<List<String>, GitBranch>

class GitBranchesTreePopupStep(private val project: Project, private val repository: GitRepository) : PopupStep<Any> {

  private var finalRunnable: Runnable? = null

  val treeModel: TreeModel = BranchesTreeModel(project, repository)

  fun getPreferredSelection(): TreePath? {
    return (treeModel as BranchesTreeModel).getPreferredSelection()
  }

  fun setBranchesMatcher(matcher: MinusculeMatcher?) {
    (treeModel as BranchesTreeModel).branchesMatcher = matcher
  }

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
      private val branchManager = project.service<GitBranchManager>()

      private val repositoryNode = Repository()

      private val structureCache = IdentityHashMap<Any, List<AbstractTreeNode<*>>>()

      private val localBranches = ClearableLazyValue.create {
        processLocal(repository.branches.localBranches)
      }

      private val remoteBranches = ClearableLazyValue.create {
        processRemote(repository.branches.remoteBranches)
      }

      var branchesMatcher by observable<MinusculeMatcher?>(null) { _, _, _ ->
        structureCache.keys.removeIf {
          !(it is Repository || it is Action)
        }
        localBranches.drop()
        remoteBranches.drop()
        treeStructureChanged(TreePath(arrayOf(root, RemoteBranches())), null, null)
        treeStructureChanged(TreePath(arrayOf(root, LocalBranches())), null, null)
      }

      private fun processLocal(branches: Collection<GitLocalBranch>): BranchesWithPreference<GitLocalBranch> {
        val matcher = branchesMatcher ?: return processDefaultPreference(branches)
        return processMatcherPreference(branches, matcher)
      }

      private fun processDefaultPreference(branches: Collection<GitLocalBranch>): BranchesWithPreference<GitLocalBranch> {
        val recentBranches = GitVcsSettings.getInstance(project).recentBranchesByRepository
        val recentBranch = recentBranches[repository.root.path]?.let { recentBranchName ->
          branches.find { it.name == recentBranchName }
        }
        if (recentBranch != null) {
          return BranchesWithPreference(branches, recentBranch to Int.MAX_VALUE)
        }

        val currentBranch = repository.currentBranch
        if (currentBranch != null) {
          return BranchesWithPreference(branches, currentBranch to Int.MAX_VALUE)
        }

        return BranchesWithPreference(branches)
      }

      private fun processRemote(branches: Collection<GitRemoteBranch>): BranchesWithPreference<GitRemoteBranch> {
        val matcher = branchesMatcher ?: return BranchesWithPreference(branches.toList())
        return processMatcherPreference(branches, matcher)
      }

      private fun <B : GitBranch> processMatcherPreference(branches: Collection<B>, matcher: MinusculeMatcher): BranchesWithPreference<B> {
        if (branches.isEmpty()) return BranchesWithPreference(branches)

        val result = mutableListOf<B>()
        var topMatch: Pair<B, Int>? = null

        for (branch in branches) {
          val matchingFragments = matcher.matchingFragments(branch.name)
          if (matchingFragments == null) continue
          result.add(branch)
          val matchingDegree = matcher.matchingDegree(branch.name, false, matchingFragments)
          if (topMatch == null || topMatch.second < matchingDegree) {
            topMatch = branch to matchingDegree
          }
        }
        return BranchesWithPreference(result, topMatch)
      }

      private data class BranchesWithPreference<B : GitBranch>(val branches: Collection<B>, val preference: Pair<B, Int>? = null)

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

      fun getPreferredSelection(): TreePath? {
        val localPreference = localBranches.value.preferFirstIfNoPreference()
        val remotePreference = remoteBranches.value.preferFirstIfNoPreference()
        if (localPreference == null && remotePreference == null) return null
        if (localPreference != null && remotePreference == null) return createTreePathFor(localPreference.first)
        if (localPreference == null && remotePreference != null) return createTreePathFor(remotePreference.first)

        if(localPreference!!.second >= remotePreference!!.second) {
          return createTreePathFor(localPreference.first)
        } else {
          return createTreePathFor(remotePreference.first)
        }
      }

      private fun BranchesWithPreference<*>.preferFirstIfNoPreference() =
        let { (branches, preference) ->
          when {
            preference != null -> preference
            branches.isNotEmpty() -> branches.first() to Int.MIN_VALUE
            else -> null
          }
        }

      private fun createTreePathFor(branch: GitBranch): TreePath {
        val root = root as NodeDescriptor<*>
        val group = (if (branch.isRemote) RemoteBranches() else LocalBranches())

        val path = mutableListOf<NodeDescriptor<*>>().apply {
          add(root)
          add(group)
        }
        val nameParts = branch.name.split('/')
        nameParts.subList(0, nameParts.size - 1).forEach {
          val folder = Folder(it) { error("Cannot load children for dummy folder node") }
          path.add(folder)
        }
        path.add(Branch(branch, nameParts.last()))
        return TreePathUtil.convertCollectionToTreePath(path)
      }

      private inner class Repository : Node<GitRepository>(project, repository) {

        init {
          myName = value.toString()
        }

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

        init {
          myName = value.text
        }

        override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value.text
          //todo: renderer has to be offset to the left somehow
          //presentation.setIcon(value.getIcon(false))
          presentation.tooltip = value.tooltip
        }
      }

      private inner class LocalBranches : Node<String>(project, "Local") {

        init {
          myName = value
        }

        override fun getChildren() = localBranches.value.branches
          .map { it.name.split('/') to it }
          .toNodes()

        override fun update(presentation: PresentationData) {
          presentation.presentableText = GitBundle.message("group.Git.Local.Branch.title")
        }
      }

      private inner class RemoteBranches : Node<String>(project, "Remote") {

        init {
          myName = value
        }

        override fun getChildren() = remoteBranches.value.branches
          .map { it.name.split('/') to it }
          .toNodes()

        override fun update(presentation: PresentationData) {
          presentation.presentableText = GitBundle.message("group.Git.Remote.Branch.title")
        }
      }

      private inner class Folder(folderName: String, private val branchesSupplier: () -> List<PathAndBranch>)
        : Node<String>(project, folderName) {

        init {
          myName = value
        }

        override fun getChildren() = branchesSupplier().toNodes()

        override fun update(presentation: PresentationData) {
          presentation.presentableText = value
          presentation.setIcon(PlatformIcons.FOLDER_ICON)
        }
      }

      private inner class Branch(branch: GitBranch, private val displayName: String)
        : Node<GitBranch>(project, branch) {

        init {
          myName = displayName
        }

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

      private val BRANCH_NODES_COMPARATOR = compareBy<Branch> {
        !branchManager.isFavorite(GitBranchType.of(it.value), repository, it.value.name)
      } then compareBy { it.name }

      private fun List<PathAndBranch>.toNodes(): List<Node<*>> {

        val branches = mutableListOf<Branch>()
        val groupsByPrefix = mutableMapOf<String, List<PathAndBranch>>()

        for ((pathParts, branch) in this) {
          val firstPathPart = pathParts.first()
          if (pathParts.size <= 1) {
            branches.add(Branch(branch, firstPathPart))
            continue
          }

          groupsByPrefix.compute(firstPathPart) { _, currentList ->
            (currentList ?: mutableListOf()) + (pathParts to branch)
          }
        }

        val folders = mutableListOf<Folder>()
        for ((prefix, branchesWithPaths) in groupsByPrefix) {
          folders.add(Folder(prefix) {
            branchesWithPaths.map { (path, branch) -> path.tail() to branch }
          })
        }

        return branches.sortedWith(BRANCH_NODES_COMPARATOR) + folders.sortedWith(compareBy { it.value })
      }
    }
  }
}