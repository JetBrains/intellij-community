package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.bookmarksToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='InternalDecoratorImpl'][.//div[@class='BookmarksView']]", BookmarksToolWindowUiComponent::class.java)

class BookmarksToolWindowUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree
    get() = x(".//div[@class='DnDAwareTree']", JTreeUiComponent::class.java)

  fun rightClickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    findBookmarkWithText(text, fullMatch).let { this.rightClickRow(it.row) }
  }

  private fun findBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.collectExpandedPaths().single {
    if (fullMatch) it.path.last() == text else it.path.last().contains(text, true)
  }
}