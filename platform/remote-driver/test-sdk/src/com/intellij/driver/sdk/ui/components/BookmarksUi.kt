package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.withRetries
import org.intellij.lang.annotations.Language
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

fun Finder.bookmarksToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='InternalDecoratorImpl'][.//div[@class='BookmarksView']]", BookmarksToolWindowUiComponent::class.java)

class BookmarksToolWindowUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree
    get() = x(".//div[@class='DnDAwareTree']", JTreeUiComponent::class.java)

  fun rightClickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    rightClickRow(findBookmarkWithText(text, fullMatch).row)
  }

  fun clickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    clickRow(findBookmarkWithText(text, fullMatch).row)
  }

  fun doubleClickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    doubleClickRow(findBookmarkWithText(text, fullMatch).row)
  }

  fun pressMoreButton() {
    actionMenuAppearance()
    x("//div[@class='ActionButton' and @myicon='moreVertical.svg']").click()
  }

  fun expandAllBookmarksTree() {
    actionMenuAppearance()
    x("//div[@class='ActionButton' and @myicon='expandAll.svg']").click()
  }

  private fun actionMenuAppearance(){
    moveMouse()
    moveMouse(Point(component.x + 20, component.y - 20))
  }

  private fun findBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.collectExpandedPaths().single {
    if (fullMatch) it.path.last() == text else it.path.last().contains(text, true)
  }
}

fun Finder.bookmarksMnemonicGrid(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyContentPanel'][//div[@class='BookmarkLayoutGrid']]", BookmarksGridLayoutUiComponent::class.java)

class BookmarksGridLayoutUiComponent(data: ComponentData) : UiComponent(data) {

  val textField
    get() = x("//div[@class='JBTextField']")

  fun findButton(text: String) = x("//div[@class='JButton' and @text='$text']", JButtonUIComponent::class.java)

  fun clickButton(text: String) = findButton(text).click()

  fun doubleClickButton(text: String) = findButton(text).doubleClick()

  class JButtonUIComponent(data: ComponentData) : UiComponent(data)
}

fun Finder.bookmarksPopup(@Language("xpath") xpath: String? = null) =
  x(xpath
    ?: "//div[@class='HeavyWeightWindow'][//div[@class='EngravedLabel' and @text='Bookmarks']]", BookmarksPopupUiComponent::class.java)

class BookmarksPopupUiComponent(data: ComponentData) : UiComponent(data) {

  private val bookmarksTree
    get() = tree("//div[@class='DnDAwareTree']")

  fun getBookmarksList() = bookmarksTree.collectExpandedPaths()
    .map { it.toString().replace("TreePathToRow{path=[", "").dropLast(2) }

  fun clickBookmark(textContains: String, doubleClick: Boolean = false) =
    bookmarksTree.waitAnyTextsContains(text = textContains).first().apply { if (doubleClick) {
      withRetries(times = 2) {
        doubleClick()
        waitNotFound(2.seconds)
      }
    } else click() }
}