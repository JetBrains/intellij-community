package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.vcs.JChangesListViewUi
import com.intellij.driver.sdk.ui.shouldBe

class CommitToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  val changeListView: JChangesListViewUi =
    x(JChangesListViewUi::class.java) { byJavaClass("com.intellij.openapi.vcs.changes.LocalChangesListView") }
  private val commitActionsPanel: CommitActionsPanelUi =
    x(CommitActionsPanelUi::class.java) { byClass("CommitActionsPanel") }
  val commitButton: UiComponent get() = commitActionsPanel.commitButton
  val commitAndPushButton: UiComponent get() = commitActionsPanel.commitAndPushButton
  val amendSpecificCommitLink: JButtonUiComponent get() = button { byClass("AmendCommitModeDropDownLink") }

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

  fun shouldContainFile(fileName: String): CommitToolWindowUi {
    changeListView.shouldBe("Commit changes tree should contain $fileName") {
      pathExists("Changes", fileName)
    }
    return this
  }

  fun shouldNotContainFile(fileName: String): CommitToolWindowUi {
    changeListView.shouldBe("Commit changes tree should not contain $fileName") {
      !pathExists("Changes", fileName)
    }
    return this
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

  private class CommitActionsPanelUi(data: ComponentData) : UiComponent(data) {
    val commitButton: UiComponent = x { byClass("MainButton") and byVisibleText("Commit") }
    val commitAndPushButton: UiComponent = x { byVisibleText("Commit and Push…") }
  }
}