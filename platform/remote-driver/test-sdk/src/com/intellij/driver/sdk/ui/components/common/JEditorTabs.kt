package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.TabInfoRef
import com.intellij.driver.sdk.ui.components.elements.TabLabelUi
import com.intellij.driver.sdk.ui.components.elements.actionButton
import org.intellij.lang.annotations.Language

fun Finder.editorTabs(@Language("xpath") xpath: String? = null, action: EditorTabsUiComponent.() -> Unit = {}) =
  x(xpath ?: "//div[@class='EditorTabs']", EditorTabsUiComponent::class.java).apply(action)

class EditorTabsUiComponent(data: ComponentData) : UiComponent(data) {

  private val editorTabsComponent by lazy { driver.cast(component, EditorTabsRef::class) }
  val editorAndPreviewActionButton: ActionButtonUi = actionButton { byAccessibleName("Editor and Preview") }
  val selectedTabInfo get() = editorTabsComponent.getSelectedInfo()

  fun getTabs() = editorTabsComponent.getTabs().map { Tab(it) }

  fun tab(accessibleName: String, fullMatch: Boolean = true): TabLabelUi =
    if (fullMatch) x(TabLabelUi::class.java) { and(byType(TYPE_EDITOR_TAB), byAccessibleName(accessibleName)) }
    else x(TabLabelUi::class.java) { and(byType(TYPE_EDITOR_TAB), contains(byAccessibleName(accessibleName))) }

  fun getTabsComponents(): List<TabLabelUi> = xx(TabLabelUi::class.java) { byType(TYPE_EDITOR_TAB) }.list().filter {
    it.component.width > 0 && it.component.height > 0
  }.sortedWith(compareBy<TabLabelUi> { it.component.x }.thenComparing { it.component.y })

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
    driver.invokeAction("CloseAllEditors", component = component)
  }

  fun isTabOpened(text: String) = getTabs().any { it.text == text }

  class Tab(private val data: TabInfoRef) {
    val text: String
      get() = data.text
    val fontSize: Int
      get() = data.getFontSize()
  }

  private companion object {
    val TYPE_EDITOR_TAB = "com.intellij.openapi.fileEditor.impl.EditorTabLabel"
  }
}

@Remote("com.intellij.openapi.fileEditor.impl.EditorTabs")
interface EditorTabsRef {
  fun getTabs(): List<TabInfoRef>
  fun getSelectedInfo(): TabInfoRef?
}
