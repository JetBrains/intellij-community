package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.list
import javax.swing.JList

class GitWorktreesToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  fun worktreesList(): JListUiComponent = list {
    and(byType(JList::class.java), byAccessibleName("Worktrees"))
  }

  fun newWorktreeButton(): ActionButtonUi = actionButton { byAccessibleName("New Worktree…") }

  fun refreshWorktreesButton(): ActionButtonUi = actionButton { byAccessibleName("Refresh") }

  fun pruneWorktreesButton(): ActionButtonUi = actionButton { byAccessibleName("Prune") }

  fun deleteWorktreeButton(): ActionButtonUi = actionButton { byAccessibleName("Delete…") }
}
