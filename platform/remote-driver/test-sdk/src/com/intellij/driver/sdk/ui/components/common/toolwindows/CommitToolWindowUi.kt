package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.vcs.JChangesListViewUi

class CommitToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  val changeListView: JChangesListViewUi =
    x(JChangesListViewUi::class.java) { byJavaClass("com.intellij.openapi.vcs.changes.LocalChangesListView") }
  val commitButton: UiComponent =
    x { componentWithChild(byClass("CommitActionsPanel"), byClass("MainButton") and byVisibleText("Commit")) }
  val commitAndPushButton: UiComponent =
    x { componentWithChild(byClass("CommitActionsPanel"), byVisibleText("Commit and Push…")) }

  val rollbackToolbarButton: UiComponent =
    x {
      byClass("ActionButton") and (contains(byAccessibleName("Revert")) or contains(byAccessibleName("Rollback")) or byAttribute("myaction.key",
                                                                                                                                 "action.ChangesView.Revert.text"))
    }

  fun rightClickFile(fileName: String) {
    changeListView.rightClickPath("Changes", fileName, fullMatch = false)
  }

  fun commitEditor(block: JEditorUiComponent.() -> Unit = {}): JEditorUiComponent =
    x(JEditorUiComponent::class.java) { byAccessibleName("Commit Message") }.apply { block() }

  fun selectFile(fileName: String) {
    changeListView.addFileName(fileName)
  }

  fun openDiffForFile(fileName: String) {
    changeListView.doubleClickPath("Changes", fileName, fullMatch = false)
  }

  fun unselectFile(fileName: String) {
    changeListView.removeFileName(fileName)
  }

  fun clickRollbackToolbarButton() {
    rollbackToolbarButton.click()
  }
}