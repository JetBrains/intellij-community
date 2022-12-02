// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.psi.codeStyle.MinusculeMatcher
import git4idea.GitBranch
import git4idea.branch.GitBranchType
import git4idea.repo.GitRepository
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

interface GitBranchesTreeModel : TreeModel {

  var isPrefixGrouping: Boolean

  fun getPreferredSelection(): TreePath?

  fun filterBranches(type: GitBranchType? = null, matcher: MinusculeMatcher? = null) {}

  object TreeRoot
  data class BranchesPrefixGroup(val type: GitBranchType, val prefix: List<String>, val repository: GitRepository? = null)
  data class BranchTypeUnderRepository(val repository: GitRepository, val type: GitBranchType)
  data class BranchUnderRepository(val repository: GitRepository, val branch: GitBranch)
}
