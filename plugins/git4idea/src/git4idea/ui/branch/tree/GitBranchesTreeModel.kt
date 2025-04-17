// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitTag
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import javax.swing.Icon
import javax.swing.tree.TreePath

internal abstract class GitBranchesTreeModel(
  protected val project: Project,
  private val actions: List<Any>,
  protected val repositories: List<GitRepository>,
) : AbstractTreeModel() {
  protected var actionsTree: LazyActionsHolder = LazyActionsHolder(project, emptyList(), null)
  protected var localBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch> = LazyRefsSubtreeHolder.emptyHolder()
  protected var remoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch> = LazyRefsSubtreeHolder.emptyHolder()
  protected var tagsTree: LazyRefsSubtreeHolder<GitTag> = LazyRefsSubtreeHolder.emptyHolder()
  protected var recentCheckoutBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch> = LazyRefsSubtreeHolder.emptyHolder()

  protected val branchesTreeCache = mutableMapOf<Any, List<Any>>()

  protected var nameMatcher: MinusculeMatcher? = null
    private set

  private val branchManager = project.service<GitBranchManager>()
  var isPrefixGrouping: Boolean by equalVetoingObservable(branchManager.isGroupingEnabled(GROUPING_BY_DIRECTORY)) {
    applyFilterAndRebuild(null)
  }

  fun init() {
    applyFilterAndRebuild(null)
  }

  fun applyFilterAndRebuild(matcher: MinusculeMatcher?) {
    nameMatcher = matcher
    rebuild(matcher)
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  protected open fun rebuild(matcher: MinusculeMatcher?) {
    branchesTreeCache.keys.clear()

    val localBranches = getLocalBranches()
    val remoteBranches = getRemoteBranches()
    val localFavorites = branchManager.getFavoriteBranches(GitBranchType.LOCAL)
    val remoteFavorites = branchManager.getFavoriteBranches(GitBranchType.REMOTE)

    val recentBranches = getRecentBranches()
    actionsTree = LazyActionsHolder(project, actions, matcher)
    localBranchesTree = LazyRefsSubtreeHolder(
      repositories,
      localBranches,
      localFavorites,
      matcher,
      ::isPrefixGrouping,
      { recentBranches?.contains(it) ?: false }
    )
    remoteBranchesTree = LazyRefsSubtreeHolder(repositories, remoteBranches, remoteFavorites, matcher, ::isPrefixGrouping)
    rebuildTags(matcher)
  }

  abstract fun getPreferredSelection(): TreePath?

  final override fun getRoot(): Any = TreeRoot

  final override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  final override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  final override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitReference
                                               || node is RefUnderRepository
                                               || (node is GitRefType && getCorrespondingTree(node).isEmpty())

  protected abstract fun getChildren(parent: Any?): List<Any>

  fun updateTags() {
    val indexOfTagsNode = getIndexOfChild(root, GitTagType)
    rebuildTags(nameMatcher)
    branchesTreeCache.keys.clear()
    val pathChanged = if (indexOfTagsNode < 0) TreePath(arrayOf(root)) else TreePath(arrayOf(root, GitTagType))
    treeStructureChanged(pathChanged, null, null)
  }

  protected abstract fun getLocalBranches(): Collection<GitLocalBranch>

  protected abstract fun getRemoteBranches(): Collection<GitRemoteBranch>

  /**
   * @return null if recent branches are not displayed
   */
  protected open fun getRecentBranches(): Collection<GitLocalBranch>? = null

  protected abstract fun getTags(): Collection<GitTag>

  protected fun areRefTreesEmpty() = (GitBranchType.entries + GitTagType).all { getCorrespondingTree(it).isEmpty() }

  protected fun getCorrespondingTree(refType: GitRefType): Map<String, Any> = when (refType) {
    GitBranchType.REMOTE -> remoteBranchesTree.tree
    GitBranchType.RECENT -> recentCheckoutBranchesTree.tree
    GitBranchType.LOCAL -> localBranchesTree.tree
    GitTagType -> tagsTree.tree
  }

  private fun rebuildTags(matcher: MinusculeMatcher?) {
    val favoriteTags = branchManager.getFavoriteBranches(GitTagType)
    tagsTree = LazyRefsSubtreeHolder(repositories, getTags(), favoriteTags, matcher, ::isPrefixGrouping)
  }

  object TreeRoot : PathElementIdProvider {
    const val NAME = "TreeRoot"
    override fun getPathElementId(): String = NAME
  }
  data class BranchesPrefixGroup(val type: GitRefType,
                                 val prefix: List<String>,
                                 val repository: GitRepository? = null) : PathElementIdProvider {
    override fun getPathElementId(): String = type.name + "/" + prefix.toString()
  }
  data class RefTypeUnderRepository(val repository: GitRepository, val type: GitRefType)

  data class RepositoryNode(
    val repository: GitRepository,
    /**
     * Set to true if this repository node doesn't contain children (e.g., used to navigate to the next level pop-up).
     */
    val isLeaf: Boolean,
  ) : PresentableNode {
    override fun getPresentableText(): String = DvcsUtil.getShortRepositoryName(repository)
  }

  data class RefUnderRepository(val repository: GitRepository, val ref: GitReference): PresentableNode {
    override fun getPresentableText(): String = ref.name
  }

  interface PresentableNode : ItemPresentation {
    override fun getLocationString(): String? = null
    override fun getIcon(unused: Boolean): Icon? = null
  }

  /**
   * Determines whether a given node is selectable.
   * Such "selectable" nodes may have a special handling in implementations: e.g., have custom icons in tree renderers or custom navigation.
   *
   * @param node node to check.
   * @return true if the node is selectable, false otherwise.
   */
  fun isSelectable(node: Any?): Boolean {
    val userValue = node ?: return false
    return (userValue is RepositoryNode && (userValue.isLeaf || this !is GitBranchesTreeMultiRepoFilteringModel)) ||
           userValue is GitReference ||
           userValue is RefUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }
}
