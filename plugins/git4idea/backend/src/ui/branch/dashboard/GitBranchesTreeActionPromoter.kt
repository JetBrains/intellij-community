// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import git4idea.actions.branch.GitRenameBranchAction

internal class GitBranchesTreeActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    val selection = context.getData(GIT_BRANCHES_TREE_SELECTION)
    if (selection?.selectedBranches.isNullOrEmpty()) return emptyList()

    return actions.filterIsInstance<GitRenameBranchAction>()
  }
}
