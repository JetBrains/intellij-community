package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.xQuery
import java.awt.Window
import javax.swing.JList


fun IdeaFrameUI.floatingToolbar(action: FloatingToolbarUi.() -> Unit = {}): FloatingToolbarUi =
  x(FloatingToolbarUi::class.java) { componentWithChild(byType(Window::class.java), byAccessibleName("Extract")) }.apply(action)

class FloatingToolbarUi(data: ComponentData) : PopupUiComponent(data) {
  val bulbActionButton = actionButton { byAccessibleName("Show Context Actions") }
  val extractActionButton = actionButton { byAccessibleName("Extract") }
  val surroundActionButton = actionButton { byAccessibleName("Surround") }
  val commentActionButton = actionButton { byAccessibleName("Comment with Line Comment") }
  val reformatCodeActionButton = actionButton { byAccessibleName("Reformat Code") }
  val moreActionButton = actionButton { byAccessibleName("More") }

  fun popupList(action: PopupUiComponent.() -> Unit = {}) =
    popup(xQuery { componentWithChild(byType(Window::class.java), byType(JList::class.java)) }).apply(action)
}