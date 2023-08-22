// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.branch.BranchType
import com.intellij.ide.util.treeView.PathElementIdProvider
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.codeStyle.MinusculeMatcher
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
}
