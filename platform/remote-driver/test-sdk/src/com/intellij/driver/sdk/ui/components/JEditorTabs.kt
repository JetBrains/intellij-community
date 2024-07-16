package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language


fun Finder.editorTabs(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='EditorTabs']", EditorTabsUiComponent::class.java)

class EditorTabsUiComponent(data: ComponentData) : UiComponent(data) {

  private val editorTabsComponent by lazy { driver.cast(component, EditorTabsRef::class) }

  fun getTabs() = editorTabsComponent.getTabs().map { Tab(it) }

  fun clickTab(text: String) {
    x("//div[@class='EditorTabLabel'][.//div[@visible_text='$text']]").click()
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
  }
}

@Remote("com.intellij.openapi.fileEditor.impl.EditorTabs")
interface EditorTabsRef {
  fun getTabs(): List<TabInfoRef>
}

@Remote("com.intellij.ui.tabs.TabInfo")
interface TabInfoRef {
  fun getText(): String
}
