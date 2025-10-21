package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.TreePathToRow
import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.withRetries
import javax.swing.JTree
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.bookmarksToolWindow(action: BookmarksToolWindowUiComponent.() -> Unit = {}): BookmarksToolWindowUiComponent =
  x(BookmarksToolWindowUiComponent::class.java) { byAccessibleName("Bookmarks Tool Window") }.apply(action)

private fun Finder.bookmarksTree(locator: QueryBuilder.() -> String = { byType(JTree::class.java) }): JTreeUiComponent =
  x(xQuery { locator() }, BookmarksJTreeUiComponent::class.java).apply {
    replaceCellRendererReader { driver.new(AccessibleNameCellRendererReader::class, rdTarget = (it as RefWrapper).getRef().rdTarget) }
  }

class BookmarksToolWindowUiComponent(data: ComponentData) : ToolWindowUiComponent(data) {

  val bookmarksTree: JTreeUiComponent by lazy {
    if (isRemDevMode) {
      wait(1.seconds) // wait till tree initialization
      bookmarksTree().expandAll() // Expand bookmark tree in SplitMode IJPL-211105
    }
    bookmarksTree()
  }
}

fun IdeaFrameUI.bookmarksPopup(action: BookmarksPopupUiComponent.() -> Unit = {}): BookmarksPopupUiComponent =
  x("//div[@class='HeavyWeightWindow'][//div[@class='EngravedLabel' and @text='Bookmarks']]", BookmarksPopupUiComponent::class.java).apply(action)

class BookmarksPopupUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree: JTreeUiComponent = bookmarksTree()

  fun clickBookmark(predicate: (String) -> Boolean) {
    bookmarksTree.clickRow(predicate = predicate)
  }

  fun doubleClickBookmark(predicate: (String) -> Boolean) {
    withRetries(times = 3, onError = { wait(1.seconds) }) {
      bookmarksTree.doubleClickRow(predicate = predicate)
      waitNotFound(3.seconds)
    }
  }
}

private class BookmarksJTreeUiComponent(data: ComponentData) : JTreeUiComponent(data) {
  override fun collectExpandedPaths(): List<TreePathToRow> {
    return super.collectExpandedPaths().map {
      it.apply {
        path = path.map { rowValue -> rowValue.substringBeforeLast(",") }
      }
    }
  }
}