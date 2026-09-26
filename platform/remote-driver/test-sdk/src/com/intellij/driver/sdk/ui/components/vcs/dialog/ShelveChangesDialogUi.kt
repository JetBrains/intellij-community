package com.intellij.driver.sdk.ui.components.vcs.dialog

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui

fun Finder.shelveChangesDialog(action: ShelveChangesDialogUi.() -> Unit = {}): ShelveChangesDialogUi =
  x("//div[@title='Shelve Changes']", ShelveChangesDialogUi::class.java).apply(action)

class ShelveChangesDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Shelve Changes"

  val shelveButton: UiComponent
    get() = x("//div[@class='JButton' and @visible_text='$primaryButtonText']")

  fun changesTree(): JTreeUiComponent = tree("//div[contains(@class,'ChangesBrowserTreeList')]")

  val showDiffButton: ActionButtonUi get() = toolbarButton("Show Diff")

  val revertButton: ActionButtonUi
    get() = actionButton { or(contains(byAccessibleName("Rollback")), contains(byAccessibleName("Revert"))) }

  val refreshButton: ActionButtonUi get() = toolbarButton("Refresh")
  val groupByButton: ActionButtonUi get() = toolbarButton("Group By")
  val expandAllButton: ActionButtonUi get() = toolbarButton("Expand All")
  val collapseAllButton: ActionButtonUi get() = toolbarButton("Collapse All")

  fun changelistChooser(): JComboBoxUiComponent = comboBox()

  fun selectChange(fileName: String) {
    changesTree().apply {
      expandAll()
      clickRow { it.contains(fileName) }
    }
  }

  fun toggleGroupBy(option: String) {
    groupByButton.click()
    driver.ui.popup().waitOneContainsText(option).click()
  }

  private fun toolbarButton(accessibleName: String): ActionButtonUi =
    actionButton { contains(byAccessibleName(accessibleName)) }
}
