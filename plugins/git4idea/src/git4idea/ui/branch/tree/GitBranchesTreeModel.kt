// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.tree.AbstractTreeModel
import git4idea.GitReference
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import javax.swing.Icon
import javax.swing.tree.TreePath

abstract class GitBranchesTreeModel : AbstractTreeModel() {
  protected val branchesTreeCache = mutableMapOf<Any, List<Any>>()
  protected open val nameMatcher: MinusculeMatcher? = null

  abstract var isPrefixGrouping: Boolean

  abstract fun getPreferredSelection(): TreePath?

  open fun filterBranches(matcher: MinusculeMatcher? = null) {}

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  protected abstract fun getChildren(parent: Any?): List<Any>

  fun updateTags() {
    val indexOfTagsNode = getIndexOfChild(root, GitTagType)
    initTags(nameMatcher)
    branchesTreeCache.keys.clear()
    val pathChanged = if (indexOfTagsNode < 0) TreePath(arrayOf(root)) else TreePath(arrayOf(root, GitTagType))
    treeStructureChanged(pathChanged, null, null)
  }

  protected abstract fun initTags(matcher: MinusculeMatcher?)

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

  data class TopLevelRepository(val repository: GitRepository): PresentableNode {
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
    return (userValue is GitRepository && this !is GitBranchesTreeMultiRepoFilteringModel) ||
           userValue is TopLevelRepository ||
           userValue is GitReference ||
           userValue is RefUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }
}
