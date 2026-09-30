package com.intellij.driver.sdk.ui.components.vcs.git

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

private const val OPTIONS_POPUP_LIST = $$"com.intellij.ui.popup.list.ListPopupImpl$MyList"

fun Finder.gitMergeDialog(action: GitMergeDialogUi.() -> Unit = {}): GitMergeDialogUi {
  return x(GitMergeDialogUi::class.java) { byClass("MyDialog") and contains(byTitle("Merge")) }.apply(action)
}

class GitMergeDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Merge"

  private val branchComboBox: JComboBoxUiComponent
    get() = comboBox { byClass("ComboBoxWithAutoCompletion") }

  val commitMessageField: UiComponent
    get() = x { byClass("JBTextArea") }

  val modifyOptionsLink: UiComponent
    get() = x { byClass("DropDownLink") and byVisibleText("Modify options") }

  fun selectBranch(branchName: String) {
    branchComboBox.selectItemContains(branchName)
  }

  fun confirm() {
    okButton.click()
    waitFor("Merge dialog to close") { notPresent() }
  }

  private fun getActiveOptions(): List<UiComponent> {
    return xx { byClass("OptionButton") }.list()
  }

  fun deselectAllOptions() {
    // Option chips are removed asynchronously, so remove them one by one and re-query until none is left,
    // otherwise a subsequent option selection may still see the stale (incompatible) options as active
    waitFor("All merge options are deselected", timeout = 15.seconds) {
      val activeOptions = getActiveOptions()
      if (activeOptions.isEmpty()) return@waitFor true
      activeOptions.first().x { byAccessibleName("Remove option") }.click()
      false
    }
  }

  fun getActiveOptionTexts(): List<String> {
    return getActiveOptions().map { it.getAllTexts().joinToString(" ") { text -> text.text } }
  }

  fun selectOption(optionFlag: String) {
    modifyOptionsLink.click()
    optionsPopupList().clickItem(optionFlag, fullMatch = false)
    waitFor("Option '$optionFlag' is active") { getActiveOptionTexts().any { it.contains(optionFlag) } }
    closeOptionsPopup()
  }

  /**
   * Closes the options popup if it is open.
   * In the Split mode, the popup closes after the click on an item, and an Escape key would close the dialog.
   */
  fun closeOptionsPopup() {
    if (optionsPopupList().notPresent()) return
    driver.ui.keyboard { escape() }
    waitFor("Options popup is closed") { optionsPopupList().notPresent() }
  }

  /**
   * The list of the options popup. The popup is a separate window, so the search starts from the IDE root, not from this dialog.
   */
  private fun optionsPopupList(): JListUiComponent = driver.ui.list { byType(OPTIONS_POPUP_LIST) }

  fun cancel() {
    cancelButton.click()
  }
}
