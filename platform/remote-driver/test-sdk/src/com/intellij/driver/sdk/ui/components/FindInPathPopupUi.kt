package com.intellij.driver.sdk.ui.components

import java.awt.Window
import javax.swing.text.JTextComponent

fun IdeaFrameUI.findInPathPopup(block: FindInPathPopupUi.() -> Unit = {}) =
  x(FindInPathPopupUi::class.java) {
    componentWithChild(byType(Window::class.java), byType("com.intellij.find.impl.FindPopupPanel"))
  }.apply(block)

class FindInPathPopupUi(data: ComponentData): DialogUiComponent(data) {
  val searchTextField: JTextFieldUI = textField { and(byType(JTextComponent::class.java), byAccessibleName("Search")) }
  val resultsTable: JTableUiComponent = table()
  val pinWindowButton = actionButton { byAccessibleName("Pin Window") }

  fun focus() {
    x { or(byAccessibleName("Find in Files"), byAccessibleName("Replace in Files")) }.click()
  }

  fun previewPanel(block: UiComponent.() -> Unit = {}): UiComponent =
    x { byType("com.intellij.usages.impl.UsagePreviewPanel") }.apply(block)
}