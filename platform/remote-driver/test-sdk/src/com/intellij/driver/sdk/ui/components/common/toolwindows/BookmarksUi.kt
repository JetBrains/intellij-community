package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.withRetries
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.bookmarksToolWindow(action: BookmarksToolWindowUiComponent.() -> Unit = {}) =
  x(BookmarksToolWindowUiComponent::class.java) { byAccessibleName("Bookmarks Tool Window") }.apply(action)

class BookmarksToolWindowUiComponent(data: ComponentData) : ToolWindowUiComponent(data) {

  val bookmarksTree by lazy {
    if (isRemDevMode) {
      wait(1.seconds) // wait till tree initialization
    }
    accessibleTree()
  }
}

fun IdeaFrameUI.bookmarksPopup(action: BookmarksPopupUiComponent.() -> Unit = {}) =
  x("//div[@class='HeavyWeightWindow'][//div[@class='EngravedLabel' and @text='Bookmarks']]", BookmarksPopupUiComponent::class.java).apply(action)

class BookmarksPopupUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree = accessibleTree()

  fun clickBookmark(predicate: (String) -> Boolean) {
    bookmarksTree.clickRow(Point(5, 5), predicate)
  }

  fun doubleClickBookmark(predicate: (String) -> Boolean) {
    withRetries(times = 3) {
      bookmarksTree.doubleClickRow(Point(5, 5), predicate)
      waitNotFound()
    }
  }
}