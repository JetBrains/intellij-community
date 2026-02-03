package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery

/**
 * @see [com.intellij.ui.BalloonImpl]
 */
fun Finder.balloon(visibleText: String? = null): BalloonUiComponent {
  val type = $$"com.intellij.ui.BalloonImpl$MyComponent"
  val query = xQuery { visibleText?.let { componentWithChild(byType(type), byVisibleText(it)) } ?: byType(type) }
  return x(query, BalloonUiComponent::class.java)
}

class BalloonUiComponent(data: ComponentData) : UiComponent(data) {
  val header: UiComponent = x { byClass("JBLabel") }
  val headerText: String get() = header.getAllTexts().asString()

  val content: UiComponent = x { byClass("LimitedWidthEditorPane") }
  val contentText: String get() = content.getAllTexts().asString()

  val htmlContent: UiComponent = x { byClass("LimitedWidthJBHtmlPane") }
  val htmlContentText: String get() = htmlContent.getAllTexts().asString()

  val primaryButton: JButtonUiComponent = button { byClass("JButton") }

  val secondaryButton: UiComponent = x { byClass("ActionLink") }
}