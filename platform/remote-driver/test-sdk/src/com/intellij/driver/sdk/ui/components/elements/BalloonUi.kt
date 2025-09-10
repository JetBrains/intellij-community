package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language

/**
 * @see [com.intellij.ui.BalloonImpl]
 */
fun Finder.balloon(@Language("xpath") xpath: String? = null): BalloonUiComponent =
  x(xpath ?: xQuery { byType($$"com.intellij.ui.BalloonImpl$MyComponent") }, BalloonUiComponent::class.java)

class BalloonUiComponent(data: ComponentData) : UiComponent(data) {
  val header: UiComponent = x { byClass("JBLabel") }
  val headerText: String get() = header.getAllTexts().asString()

  val content: UiComponent = x { byClass("LimitedWidthEditorPane") }
  val contentText: String get() = content.getAllTexts().asString()

  val primaryButton: JButtonUiComponent = button { byClass("JButton") }

  val secondaryButton: UiComponent = x { byClass("ActionLink") }
}