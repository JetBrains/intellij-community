package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language


fun Finder.editorTabs(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='EditorTabs']", EditorTabsUiComponent::class.java)

class EditorTabsUiComponent(data: ComponentData) : UiComponent(data) {

  private val editorTabsComponent by lazy { driver.cast(component, EditorTabsRef::class) }

  fun getTabs() = editorTabsComponent.getTabs().map { Tab(it) }

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
