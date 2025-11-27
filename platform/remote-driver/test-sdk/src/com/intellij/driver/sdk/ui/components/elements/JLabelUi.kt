package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.linkLabel(labelText: String): JLabelUiComponent =
  x(JLabelUiComponent::class.java) { and(byType("com.intellij.ui.components.labels.LinkLabel"), byAccessibleName(labelText)) }

class JLabelUiComponent(data: ComponentData) : UiComponent(data) {

  private val fixture by lazy { driver.cast(component, JLabelRef::class) }

  fun getText(): String = fixture.getText().orEmpty()
}

open class TabLabelUi(data: ComponentData) : UiComponent(data) {
  protected val tabComponent get() = driver.cast(component, TabLabel::class)
  val closeButton = x { and(byType("com.intellij.ui.InplaceButton"), contains(byAccessibleName("Close"))) }
  val unpinTabButton = x { byAccessibleName("Unpin Tab") }
  val text get() = tabComponent.info.text
  val fontSize get() = tabComponent.info.getFontSize()
  val isPinned get() = tabComponent.isPinned

  fun clickClose() {
    moveMouse()
    closeButton.click()
  }
}

@Remote("javax.swing.JLabel")
interface JLabelRef {
  fun getText(): String?
}

@Remote("com.intellij.ui.tabs.impl.TabLabel")
interface TabLabel {
  val info: TabInfoRef
  val isPinned: Boolean
}

@Remote("com.intellij.ui.tabs.TabInfo")
interface TabInfoRef {
  val text: String
  fun getFontSize(): Int
}
