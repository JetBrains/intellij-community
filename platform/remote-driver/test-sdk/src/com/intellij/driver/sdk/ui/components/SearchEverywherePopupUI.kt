package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language
import javax.swing.JList
import kotlin.time.Duration.Companion.seconds


fun Finder.searchEverywherePopup(@Language("xpath") xpath: String? = null) = x(xpath ?: xQuery { componentWithChild(byClass("HeavyWeightWindow"), byClass("SearchEverywhereUI")) },
                                                                               SearchEverywherePopupUI::class.java)

class SearchEverywherePopupUI(data: ComponentData): PopupUiComponent(data) {
  val resultsList by lazy {
    x(JListUiComponent::class.java) { byType(JList::class.java) }.apply {
      replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class))
    }
  }
  val searchField: JTextFieldUI = textField { byType("com.intellij.ide.actions.BigPopupUI${"$"}SearchField") }
  val openInFindToolWindowButton: ActionButtonUi = actionButtonByXpath(xQuery { byAccessibleName("Open in Find Tool Window") })

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
}