package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.vcs.JChangesListViewUi
import com.intellij.driver.sdk.ui.xQuery

class CommitToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  val changeListView: JChangesListViewUi =
    x(xQuery { byJavaClass("com.intellij.openapi.vcs.changes.LocalChangesListView") }, JChangesListViewUi::class.java)
  val commitButton: UiComponent = x("//div[@class='CommitActionsPanel']//div[@class='MainButton' and @visible_text='Commit']")
  val commitAndPushButton: UiComponent = x("//div[@class='CommitActionsPanel']//div[@visible_text='Commit and Push…']")

  fun rightClickFile(fileName: String) {
    changeListView.rightClickPath("Changes", fileName, fullMatch = false)
  }

  fun commitEditor(block: JEditorUiComponent.() -> Unit = {}): JEditorUiComponent =
    x(xQuery { byAccessibleName("Commit Message") }, JEditorUiComponent::class.java).apply { block() }

  fun selectFile(fileName: String) {
    changeListView.addFileName(fileName)
  }

  fun openDiffForFile(fileName: String) {
    changeListView.doubleClickPath("Changes", fileName, fullMatch = false)
  }

  fun unselectFile(fileName: String) {
    changeListView.removeFileName(fileName)
  }


}