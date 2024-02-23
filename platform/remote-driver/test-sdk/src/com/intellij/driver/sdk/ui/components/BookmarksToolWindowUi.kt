package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.bookmarksToolWindow(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='InternalDecoratorImpl'][.//div[@class='BookmarksView']]", BookmarksToolWindowUiComponent::class.java)

class BookmarksToolWindowUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree
    get() = x(".//div[@class='DnDAwareTree']", JTreeUiComponent::class.java)
}