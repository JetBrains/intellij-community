package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.bookmarksMnemonicGrid(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyContentPanel'][//div[@class='BookmarkLayoutGrid']]", BookmarksGridLayoutUiComponent::class.java)

class BookmarksGridLayoutUiComponent(data: ComponentData) : UiComponent(data) {

  val textField
    get() = x("//div[@class='JBTextField']")

  private fun findButton(text: String) = x("//div[@class='JButton' and @text='$text']", JButtonUIComponent::class.java)

  fun clickButton(text: String) = findButton(text).click()

  fun doubleClickButton(text: String) = findButton(text).doubleClick()

  private class JButtonUIComponent(data: ComponentData) : UiComponent(data)
}