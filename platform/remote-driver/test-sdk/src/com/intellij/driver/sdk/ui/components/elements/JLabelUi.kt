package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.SimpleColoredText
import com.intellij.driver.sdk.SimpleTextAttributes
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.Icon

fun Finder.linkLabel(labelText: String): JLabelUiComponent =
  x(JLabelUiComponent::class.java) { and(byType("com.intellij.ui.components.labels.LinkLabel"), byAccessibleName(labelText)) }

fun Finder.contentTabLabel(labelText: String): ContentTabLabelUi =
  x(ContentTabLabelUi::class.java) { and(byClass("ContentTabLabel"), byAccessibleName(labelText)) }

fun Finder.tabbedContentTabLabel(labelText: String, contains: Boolean = false): ContentTabLabelUi =
  x(ContentTabLabelUi::class.java) {
    and(byClass("TabbedContentTabLabel"),
        if (contains) contains(byAccessibleName(labelText)) else byAccessibleName(labelText))
  }

class JLabelUiComponent(data: ComponentData) : UiComponent(data) {

  private val fixture by lazy { driver.cast(component, JLabelRef::class) }

  fun getText(): String = fixture.getText().orEmpty()
}

class ContentTabLabelUi(data: ComponentData) : UiComponent(data) {
  private val tabComponent get() = driver.cast(component, ContentTabLabel::class)
  val isSelected: Boolean get() = tabComponent.isSelected()
}

open class TabLabelUi(data: ComponentData) : UiComponent(data) {
  protected val tabComponent get() = driver.cast(component, TabLabel::class)
  val closeButton = x { and(byType("com.intellij.ui.InplaceButton"), contains(byAccessibleName("Close"))) }
  val unpinTabButton = x { byAccessibleName("Unpin Tab") }
  val text get() = tabComponent.info.text
  val fontSize get() = tabComponent.info.getFontSize()
  val isPinned get() = tabComponent.isPinned
  val hasIcon get() = tabComponent.info.getIcon() != null

  fun clickClose() {
    moveMouse()
    closeButton.click()
  }

  fun getTextAttributes(): List<Pair<String, SimpleTextAttributes>> {
    val coloredText = tabComponent.info.getColoredText()
    return coloredText.getTexts().zip(coloredText.getAttributes())
  }
}

@Remote("com.intellij.openapi.wm.impl.content.ContentTabLabel")
interface ContentTabLabel {
  fun isSelected(): Boolean
}

@Remote("javax.swing.JLabel")
interface JLabelRef {
  fun getText(): String?
  fun getIcon(): Icon?
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
  fun getIcon(): Icon?
  fun getColoredText(): SimpleColoredText
}
