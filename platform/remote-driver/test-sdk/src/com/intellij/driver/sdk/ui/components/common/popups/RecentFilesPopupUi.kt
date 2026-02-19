package com.intellij.driver.sdk.ui.components.common.popups

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.checkBox
import javax.swing.JList

fun IdeaFrameUI.recentFilesPopup(block: RecentFilesPopupUi.() -> Unit = {}): RecentFilesPopupUi =
  x(RecentFilesPopupUi::class.java) {
    componentWithChild(
      byType("javax.swing.Popup${"$"}HeavyWeightWindow"),
      byType("com.intellij.platform.recentFiles.frontend.Switcher${"$"}SwitcherPanel"))
  }.apply(block)

class RecentFilesPopupUi(data: ComponentData) : PopupUiComponent(data) {
  val showOnlyEditedFilesCheckbox = checkBox { byAccessibleName("Show edited only") }
  val filesList = accessibleList { and(byType(JList::class.java), byAccessibleName("Files")) }
  val toolWindowsList = accessibleList { and(byType(JList::class.java), byAccessibleName("Tool windows")) }
}