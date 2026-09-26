package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JBTabbedPaneUiComponent
import com.intellij.driver.sdk.ui.components.common.tabbedPane
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTextFieldUI
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButtonByXpath
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.table
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import javax.swing.JList
import kotlin.time.Duration.Companion.seconds


fun Finder.searchEverywherePopup(isSplit: Boolean = true, @Language("xpath") xpath: String? = null, block: SearchEverywherePopupUI.() -> Unit = {}): SearchEverywherePopupUI =
  if (isSplit) x(xpath ?: xQuery { componentWithChild(byClass("HeavyWeightWindow"), byClass("SePopupContentPane")) }, SearchEverywhereSplitPopupUI::class.java).apply(block)
  else x(xpath ?: xQuery { componentWithChild(byClass("HeavyWeightWindow"), byClass("SearchEverywhereUI")) }, SearchEverywherePopupUI::class.java).apply(block)

private fun Finder.seList(locator: QueryBuilder.() -> String = { byType(JList::class.java) }) =
  x(SEJListUiComponent::class.java) { locator() }.apply {
    replaceCellRendererReader { driver.new(AccessibleNameCellRendererReader::class, rdTarget = (it as RefWrapper).getRef().rdTarget) }
  }

open class SearchEverywherePopupUI(data: ComponentData) : PopupUiComponent(data) {
  val resultsList: JListUiComponent = seList()
  open val searchField: JTextFieldUI = textField { byType("com.intellij.ide.actions.BigPopupUI${"$"}SearchField") }
  val includeNonProjectItemsCheckBox: JCheckBoxUi = checkBox { byAccessibleName("Include non-project items") }
  val openInFindToolWindowButton: ActionButtonUi = actionButtonByXpath(xQuery { byAccessibleName("Open in Find Tool Window") })
  val previewButton: ActionButtonUi = actionButtonByXpath(xQuery { byAccessibleName("Preview") })
  val typeFilterButton: ActionButtonUi = actionButtonByXpath(xQuery { byAccessibleName("Filter") })
  val openInRightSplitActionLink: UiComponent = x { byAccessibleName("Open In Right Split") }
  private val tabbedPane: JBTabbedPaneUiComponent = tabbedPane()

  fun invokeSelectAction() {
    keyboard { enter() }
  }

  fun getSelectedTab(): SearchEverywhereTab = SearchEverywhereTab.entries.single { it.name == tabbedPane.selectedTabName }

  fun search(text: String) {
    step("Search for '$text' in Search Everywhere") {
      searchField.click()
      searchField.text = text
    }
  }

  fun searchAndChooseFirst(text: String, exactMatch: Boolean = true) {
    searchField.text = ""
    searchField.click()
    keyboard {
      backspace()
      keyboard { typeText(text) }
      resultsList.should(timeout = 15.seconds) {
        if (exactMatch) hasText(text) else hasSubtext(text)
      }
      enter()
    }
  }

  fun closePopup() {
    keyboard { escape() }
    waitFor("Popup is closed") { notPresent() }
  }

  fun switchTypeFilters(types: List<String>) {
    typeFilterButton.click()
    types.forEach { type ->
      searchEverywhereTypeFilterPopup().clickType(type)
    }
    typeFilterButton.click()
  }

  fun clickNoneButtonInTypeFilters() {
    typeFilterButton.click()
    searchEverywhereTypeFilterPopup().button("None").click()
    typeFilterButton.click()
  }

  fun selectTab(tab: String) {
    x { byVisibleText(tab) }.click()
  }

  enum class SearchEverywhereTab(val id: String) {
    All("All"),
    Classes("Classes"),
    Files("Files"),
    Symbols("Symbols"),
    Actions("Actions"),
  }
}

class SearchEverywhereSplitPopupUI(data: ComponentData) : SearchEverywherePopupUI(data) {
  override val searchField: JTextFieldUI = textField { byClass("SeTextField") }
}


fun Finder.searchEverywhereTypeFilterPopup(
  @Language("xpath") xpath: String? = null,
  block: SearchEverywhereTypeFilterUI.() -> Unit = {},
): SearchEverywhereTypeFilterUI =
  x(xpath ?: xQuery { componentWithChild(byClass("HeavyWeightWindow"), byClass("ElementsChooser")) },
    SearchEverywhereTypeFilterUI::class.java).apply(block)

class SearchEverywhereTypeFilterUI(data: ComponentData) : UiComponent(data) {
  fun clickType(type: String) {
    val actionsLabelRowColumn = table().findRowColumn { it == type }
    table().clickCell(actionsLabelRowColumn.first, actionsLabelRowColumn.second - 1)
  }
}

private class SEJListUiComponent(data: ComponentData) : JListUiComponent(data) {
  override val items: List<String>
    get() = super.items.map { it.substringBeforeLast(",") }

  override val selectedItems: List<String>
    get() = super.selectedItems.map { it.substringBeforeLast(",") }
}
