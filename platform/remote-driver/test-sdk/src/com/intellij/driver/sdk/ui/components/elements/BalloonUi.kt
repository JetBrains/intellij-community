package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
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
  private val htmlContentComponent by lazy { driver.cast(htmlContent.component, JBHtmlPane::class) }
  val htmlContentText: String get() = htmlContent.getAllTexts().asString()

  val primaryButton: JButtonUiComponent = button { byClass("JButton") }

  val secondaryButton: UiComponent = x { byClass("ActionLink") }

  fun extractLink(linkMustContain: String) : String {
    val htmlContent = htmlContentComponent.getText()
    val linksRegex = """href="([^"]+)"""".toRegex()
    return linksRegex.findAll(htmlContent).map { it.groupValues[1] }.firstOrNull { linkMustContain in it }
           ?: error("No link with $linkMustContain found in tooltip text:\n$htmlContent")
  }
}

@Remote("com.intellij.ui.components.JBHtmlPane")
interface JBHtmlPane {
  fun getText(): String
}