package com.intellij.driver.sdk.ui.components

import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ActionManager
import com.intellij.driver.sdk.ActionUtils
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JList
import kotlin.time.Duration.Companion.seconds


fun Finder.searchEverywherePopup(@Language("xpath") xpath: String? = null, block: SearchEverywherePopupUI.() -> Unit = {}) = x(xpath ?: xQuery { componentWithChild(byClass("HeavyWeightWindow"), byClass("SearchEverywhereUI")) },
                                                                               SearchEverywherePopupUI::class.java).apply(block)

class SearchEverywherePopupUI(data: ComponentData): PopupUiComponent(data) {
  val resultsList by lazy {
    x(JListUiComponent::class.java) { byType(JList::class.java) }.apply {
      replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class))
    }
  }
  val searchField: JTextFieldUI = textField { byType("com.intellij.ide.actions.BigPopupUI${"$"}SearchField") }
  val openInFindToolWindowButton: ActionButtonUi = actionButtonByXpath(xQuery { byAccessibleName("Open in Find Tool Window") })

  fun invokeSelectAction() {
    invokeActionWithShortcut("[pressed ENTER]")
  }

  fun invokeOpenInRightSplitAction() {
    invokeActionWithShortcut("[shift pressed ENTER]")
  }

  fun searchAndChooseFirst(text: String, exactMatch: Boolean = true) {
    keyboard {
      backspace()
      keyboard { enterText(text) }
      resultsList.should(timeout = 15.seconds) {
        if (exactMatch) hasText(text) else hasSubtext(text)
      }
      enter()
    }
  }

  private fun invokeActionWithShortcut(shortcut: String) {
    val searchEveryWhereUiComponent = x { byType("com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI") }
    val action = driver.utility(ActionUtils::class).getActions(searchEveryWhereUiComponent.component).singleOrNull {
      it.getShortcutSet().getShortcuts().singleOrNull()?.toString() == shortcut
    } ?: error("'Action with shortcut '$shortcut' was not found")
    driver.withContext(OnDispatcher.EDT) {
      service(ActionManager::class).tryToExecute(action, null, null, null, true)
    }
  }
}