package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.should
import org.intellij.lang.annotations.Language


fun Finder.searchEverywherePopup(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='HeavyWeightWindow'][//div[contains(@class, 'SearchEverywhereUI')]]",
                                                                               SearchEverywherePopupUI::class.java)

class SearchEverywherePopupUI(data: ComponentData): PopupUiComponent(data) {
  private val searchField = x("//div[@class='SearchField']")
  val resultsList = x("//div[@class='JBList']", JListUiComponent::class.java)

  fun searchAndChooseFirst(text: String) {
    keyboard {
      enterText(text)
      resultsList.should(15) { hasText(text) }
      enter()
    }
  }
}