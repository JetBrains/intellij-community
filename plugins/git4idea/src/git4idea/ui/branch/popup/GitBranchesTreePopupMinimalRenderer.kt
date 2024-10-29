// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import javax.swing.JTree

class GitBranchesTreePopupMinimalRenderer(step: GitBranchesTreePopupStepBase) :
  GitBranchesTreeRenderer(step.project, step.treeModel, step.selectedRepository, step.repositories, favoriteToggleOnClickSupported = false) {

  override val mainPanel: BorderLayoutPanel =
    JBUI.Panels.simplePanel(mainTextComponent).addToLeft(mainIconComponent).andTransparent()

  override fun configureTreeCellComponent(
    tree: JTree,
    userObject: Any?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) { }
}