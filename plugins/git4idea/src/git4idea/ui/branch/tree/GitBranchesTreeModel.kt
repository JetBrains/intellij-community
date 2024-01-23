// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.BranchType
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.popup.PopupFactoryImpl
import git4idea.GitBranch
import git4idea.repo.GitRepository
import javax.swing.Icon
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

interface GitBranchesTreeModel : TreeModel {

  var isPrefixGrouping: Boolean

  fun getPreferredSelection(): TreePath?

  fun filterBranches(matcher: MinusculeMatcher? = null) {}

  object TreeRoot : PathElementIdProvider {
    const val NAME = "TreeRoot"
    override fun getPathElementId(): String = NAME
  }
  data class BranchesPrefixGroup(val type: BranchType,
                                 val prefix: List<String>,
                                 val repository: GitRepository? = null) : PathElementIdProvider {
    override fun getPathElementId(): String = type.name + "/" + prefix.toString()
  }
  data class BranchTypeUnderRepository(val repository: GitRepository, val type: BranchType)

  data class TopLevelRepository(val repository: GitRepository): PresentableNode {
    override fun getPresentableText(): String = DvcsUtil.getShortRepositoryName(repository)
  }

  data class BranchUnderRepository(val repository: GitRepository, val branch: GitBranch): PresentableNode {
    override fun getPresentableText(): String = branch.name
  }
  object RecentNode : BranchType, PathElementIdProvider {
    const val NAME = "RECENT"
    override fun getName(): String = NAME
    override fun getPathElementId(): String = NAME
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
           userValue is GitBranch ||
           userValue is BranchUnderRepository ||
           (userValue is PopupFactoryImpl.ActionItem && userValue.isEnabled)
  }
}
