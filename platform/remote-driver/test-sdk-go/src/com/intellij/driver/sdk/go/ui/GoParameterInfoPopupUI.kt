package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

/**
 * The hint shown by the `ParameterInfo` action for the call the caret is in.
 */
fun Finder.goParameterInfoPopup(action: GoParameterInfoPopupUI.() -> Unit = {}): GoParameterInfoPopupUI {
  return x(GoParameterInfoPopupUI::class.java) { byClass("ParameterInfoComponent") }.apply(action)
}

/**
 * `ParameterInfoComponent` is a panel that renders every overload of the call in a nested html pane.
 */
class GoParameterInfoPopupUI(data: ComponentData) : UiComponent(data) {

  /**
   * Parameters of the call as a single line. The html pane emits a text fragment per style, so a signature
   * arrives split around its links and around the parameter highlighted at the caret
   * (`ctx context.Context` comes as `ctx context.` + `Context`) and the fragments are joined back here.
   */
  val signature: String
    get() = getAllTexts().joinToString("") { it.text }.replace(WHITESPACE, " ").trim()

  private companion object {
    private val WHITESPACE = Regex("\\s+")
  }
}
