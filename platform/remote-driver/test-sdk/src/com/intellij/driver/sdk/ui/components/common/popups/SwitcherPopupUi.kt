package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import javax.swing.JList

fun IdeaFrameUI.switcherPopup(block: SwitcherPopupUi.() -> Unit = {}): SwitcherPopupUi =
  x(SwitcherPopupUi::class.java) {
    componentWithChild(
      byType("javax.swing.Popup${"$"}HeavyWeightWindow"),
      byType("com.intellij.platform.recentFiles.frontend.Switcher${"$"}SwitcherPanel"))
  }.apply(block)

class SwitcherPopupUi(data: ComponentData) : PopupUiComponent(data) {
  val toolWindowList = accessibleList { and(byType(JList::class.java), byAccessibleName("Tool windows")) }
  val filesList = accessibleList { and(byType(JList::class.java), byAccessibleName("Files")) }
}