package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.*
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.*
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JList
import kotlin.time.Duration.Companion.seconds


fun Finder.searchEverywherePopup(isSplit: Boolean = false, @Language("xpath") xpath: String? = null, block: SearchEverywherePopupUI.() -> Unit = {}): SearchEverywherePopupUI =
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
  val searchEverywhereUi: SearchEveryWhereUi = x(SearchEveryWhereUi::class.java) { byType("com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI") }
  val openInRightSplitActionLink: UiComponent = x { byAccessibleName("Open In Right Split") }

  fun invokeSelectAction() {
    invokeActionWithShortcut("[pressed ENTER]")
  }

  fun invokeOpenInRightSplitAction() {
    invokeActionWithShortcut("[shift pressed ENTER]")
  }

  fun invokeSwitchToNextTabAction() {
    invokeActionWithShortcut("[pressed TAB]") { it.getOrNull(1) } // there are two actions with [tab] shortcut
  }

  fun invokeSwitchToPrevTabAction() {
    invokeActionWithShortcut("[shift pressed TAB]")
  }

  fun invokeAssignShortcutAction() {
    invokeActionWithShortcut("[alt pressed ENTER]")
  }

  fun getSelectedTab(): SearchEverywhereTab = SearchEverywhereTab.entries.single { it.id == searchEverywhereUi.getSelectedTabID() }

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
    searchEverywhereUi.closePopup()
    waitFor("Popup is closed") { notPresent() }
  }

  fun switchTypeFilters(types: List<String>) {
    typeFilterButton.click()
    types.forEach { type ->
      searchEverywhereTypeFilterPopup().clickType(type)
    }
    typeFilterButton.click()
  }

  private fun invokeActionWithShortcut(shortcut: String, chooser: (List<AnAction>) -> AnAction? = { it.singleOrNull() }) {
    val action = driver.utility(ActionUtils::class).getActions(searchEverywhereUi.component).filter {
      it.getShortcutSet().getShortcuts().singleOrNull()?.toString() == shortcut
    }.let(chooser) ?: error("'Action with shortcut '$shortcut' was not found")
    driver.withContext(OnDispatcher.EDT) {
      service(ActionManager::class).tryToExecute(action, null, null, null, true)
    }
  }

  enum class SearchEverywhereTab(val id: String) {
    All("SearchEverywhereContributor.All"),
    Classes("ClassSearchEverywhereContributor"),
    Files("FileSearchEverywhereContributor"),
    Symbols("SymbolSearchEverywhereContributor"),
    Actions("ActionSearchEverywhereContributor"),
  }

  class SearchEveryWhereUi(data: ComponentData) : UiComponent(data) {
    private val searchEverywhereUiComponent get() = driver.cast(component, SearchEverywhereUiComponent::class)

    fun getSelectedTabID(): String = searchEverywhereUiComponent.getSelectedTabID()

    fun closePopup() = driver.withContext(OnDispatcher.EDT) { searchEverywhereUiComponent.closePopup() }
  }

  @Remote("com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI")
  interface SearchEverywhereUiComponent {
    fun getSelectedTabID(): String
    fun closePopup()
  }
}

class SearchEverywhereSplitPopupUI(data: ComponentData) : SearchEverywherePopupUI(data) {
  override val searchField: JTextFieldUI = textField { byClass("SeTextField") }
}


fun Finder.searchEverywhereTypeFilterPopup(@Language("xpath") xpath: String? = null, block: SearchEverywhereTypeFilterUI.() -> Unit = {}): SearchEverywhereTypeFilterUI =
  x(xpath ?: xQuery { componentWithChild(byClass("HeavyWeightWindow"), byClass("ElementsChooser")) }, SearchEverywhereTypeFilterUI::class.java).apply(block)

class SearchEverywhereTypeFilterUI(data: ComponentData) : UiComponent(data) {
  fun clickType(type: String) {
    val actionsLabelRowColumn = table().findRowColumn { it == type }
    table().clickCell(actionsLabelRowColumn.first, actionsLabelRowColumn.second - 1)
  }
}

private class SEJListUiComponent(data: ComponentData) : JListUiComponent(data) {
  override val items: List<String>
    get() = super.items.map {it -> it.substringBeforeLast(",")}

  override val selectedItems: List<String>
    get() = super.selectedItems.map { it.substringBeforeLast(",") }
}
