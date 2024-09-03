package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds


fun Finder.searchEverywherePopup(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='HeavyWeightWindow'][//div[contains(@class, 'SearchEverywhereUI')]]",
                                                                               SearchEverywherePopupUI::class.java)

class SearchEverywherePopupUI(data: ComponentData): PopupUiComponent(data) {
  val resultsList = x("//div[@class='JBList']", JListUiComponent::class.java)
  val searchField: JTextFieldUI = textField { byType("com.intellij.ide.actions.BigPopupUI${"$"}SearchField") }
  val openInFindToolWindowButton: ActionButtonUi = actionButtonByXpath(xQuery { byAccessibleName("Open in Find Tool Window") })

  fun searchAndChooseFirst(text: String, exactMatch: Boolean = true) {
    keyboard {
      backspace()
      driver.ui.pasteText(text)
      resultsList.should(timeout = 15.seconds) {
        if (exactMatch) hasText(text) else hasSubtext(text)
      }
      enter()
    }
  }
}