package com.intellij.driver.sdk.ui.components

import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ActionManager
import com.intellij.driver.sdk.ActionUtils
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.openapi.util.SystemInfoRt
import java.awt.Window
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JTable
import javax.swing.text.JTextComponent

fun IdeaFrameUI.findInPathPopup(block: FindInPathPopupUi.() -> Unit = {}) =
  x(FindInPathPopupUi::class.java) {
    componentWithChild(byType(Window::class.java), byType(FindInPathPopupUi.TYPE_FIND_POPUP_PANEL))
  }.apply(block)

fun IdeaFrameUI.replaceInPathPopup(block: ReplaceInPathPopupUi.() -> Unit = {}) =
  x(ReplaceInPathPopupUi::class.java) {
    componentWithChild(byType(Window::class.java), byAccessibleName("Replace"))
  }.apply(block)

open class FindInPathPopupUi(data: ComponentData): DialogUiComponent(data) {
  private val searchTextArea = x { componentWithChild(byType("com.intellij.find.SearchTextArea"), byAccessibleName("Search")) }

  val matchesFoundLabel = x { contains(byAccessibleName("matches in")) }
  val fileMaskCheckBox = checkBox { byAccessibleName("File mask:") }
  val fileMaskComboBox = x { byType("com.intellij.openapi.ui.DialogPanel") }.comboBox()
  val filterSearchResultsActionButton = actionButton { byAccessibleName("Filter Search Results") }
  val pinWindowButton = actionButton { byAccessibleName("Pin Window") }

  val searchTextField: JTextFieldUI = textField { and(byType(JTextComponent::class.java), byAccessibleName("Search")) }
  val newLineActionButton = searchTextArea.actionButton { byAccessibleName("New Line") }
  val matchCaseActionButton = searchTextArea.actionButton { byAccessibleName("Match case") }
  val wordsActionButton = searchTextArea.actionButton { byAccessibleName("Words") }
  val regexActionButton = searchTextArea.actionButton { byAccessibleName("Regex") }

  val resultsTable: FindInPathResultsUi by lazy {
    x(FindInPathResultsUi::class.java) { byType(JTable::class.java) }
  }

  val inProjectActionButton = actionButton { and(byType("com.intellij.openapi.actionSystem.impl.ActionButton"), byAccessibleName("In Project")) }
  val directoryActionButton = actionButton { and(byType("com.intellij.openapi.actionSystem.impl.ActionButton"), byAccessibleName("Directory")) }
  val scopeActionButton = actionButton { and(byType("com.intellij.openapi.actionSystem.impl.ActionButton"), byAccessibleName("Scope")) }

  val directoryChooser = x { byType("com.intellij.find.impl.FindPopupDirectoryChooser") }.textField()
  val browseButton = x { byTooltip(if (SystemInfoRt.isMac) "⇧⏎" else "Shift+Enter") }
  val searchRecursivelyActionButton = actionButton { byAccessibleName("Search recursively in subdirectories") }

  val scopeChooserComboBox = x { byType("com.intellij.ide.util.scopeChooser.ScopeChooserCombo") }.comboBox()

  val openResultsInNewTabCheckBox = checkBox { byAccessibleName("Open results in new tab") }
  val openInFindWindowButton = x { byAccessibleName("Open in Find Window") }

  fun focus() {
    x { or(byAccessibleName("Find in Files"), byAccessibleName("Replace in Files")) }.click()
  }

  fun showFilterResultsPopup() {
    val findPopupPanel = x { byType(TYPE_FIND_POPUP_PANEL) }
    val action = driver.utility(ActionUtils::class).getActions(findPopupPanel.component)
      .singleOrNull { it.getTemplateText() == "Filter Search Results" } ?: error("filter search results action not found")
    driver.withContext(OnDispatcher.EDT) {
      service(ActionManager::class).tryToExecute(action, null, findPopupPanel.component, null, true)
    }
  }

  fun showSearchHistoryPopup() {
    searchTextField.click()
    keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_DOWN) }
  }

  fun previewPanel(block: UiComponent.() -> Unit = {}): UiComponent =
    x { byType("com.intellij.usages.impl.UsagePreviewPanel") }.apply(block)

  fun close() {
    focus()
    keyboard { escape() }
    waitNotFound()
  }

  class FindInPathResultsUi(data: ComponentData): JTableUiComponent(data) {
    val items: List<String> get() = content().map { row -> row.value.values.joinToString() }
    val selectedItem: String? get() = getSelectedRow().let { if (it != -1) items[it] else null }

    init {
      replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class))
    }

    fun rightClickItemAtRow(row: Int) {
      rightClickCell(row, 0)
    }
  }

  companion object {
    const val TYPE_FIND_POPUP_PANEL = "com.intellij.find.impl.FindPopupPanel"
  }
}

class ReplaceInPathPopupUi(data: ComponentData): FindInPathPopupUi(data) {
  val replaceTextField: JTextFieldUI = textField { and(byType(JTextComponent::class.java), byAccessibleName("Replace")) }
  val preserveCaseActionButton = actionButton { byAccessibleName("Preserve case") }

  val replaceAllButton = button { and(byType(JButton::class.java), byAccessibleName("Replace All")) }
  val replaceButton = button { and(byType(JButton::class.java), byAccessibleName("Replace")) }

  fun showReplaceHistoryPopup() {
    replaceTextField.click()
    keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_DOWN) }
  }
}