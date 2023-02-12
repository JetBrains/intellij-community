// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import git4idea.ui.branch.tree.GitBranchesTreeRenderer

class GitBranchesTreePopupRenderer(private val step: GitBranchesTreePopupStep) :
  GitBranchesTreeRenderer(step.project, step.treeModel, step.selectedRepository, step.repositories) {

  override fun hasRightArrow(nodeUserObject: Any?): Boolean = step.hasSubstep(nodeUserObject)
}
