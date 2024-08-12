package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language


fun Finder.editorTabs(@Language("xpath") xpath: String? = null, action: EditorTabsUiComponent.() -> Unit = {}) =
  x(xpath ?: "//div[@class='EditorTabs']", EditorTabsUiComponent::class.java).apply(action)

class EditorTabsUiComponent(data: ComponentData) : UiComponent(data) {

  private val editorTabsComponent by lazy { driver.cast(component, EditorTabsRef::class) }

  fun getTabs() = editorTabsComponent.getTabs().map { Tab(it) }

  fun getTabsComponents(): List<UiComponent> = xx { byType("com.intellij.openapi.fileEditor.impl.EditorTabLabel") }.list().filter {
    it.component.width > 0 && it.component.height > 0
  }

  fun clickTab(text: String) {
    x("//div[@class='EditorTabLabel'][.//div[@visible_text='$text']]").click()
  }

  fun doubleClickTab(text: String) {
    x("//div[@class='EditorTabLabel'][.//div[@visible_text='$text']]").doubleClick()
  }

  fun closeTab(text: String = "") {
    x("//div[@class='EditorTabLabel'][.//div[@visible_text='$text']]//div[@myicon='closeSmall.svg']")
      .click()
  }

  fun closeAllTabs() {
    xx("//div[@class='EditorTabLabel']//div[@myicon='closeSmall.svg']")
      .list().forEach { it.click() }
  }

  fun isTabOpened(text: String) = getTabs().any { it.text == text }

  inner class Tab(private val data: TabInfoRef) {
    val text: String
      get() = data.getText()
    val fontSize: Int
      get() = data.getFontSize()
  }
}

@Remote("com.intellij.openapi.fileEditor.impl.EditorTabs")
interface EditorTabsRef {
  fun getTabs(): List<TabInfoRef>
}

@Remote("com.intellij.ui.tabs.TabInfo")
interface TabInfoRef {
  fun getText(): String
  fun getFontSize(): Int
}
