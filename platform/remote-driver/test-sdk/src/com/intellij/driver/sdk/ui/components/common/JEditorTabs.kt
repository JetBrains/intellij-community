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
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language


fun Finder.editorTabs(@Language("xpath") xpath: String? = null, action: EditorTabsUiComponent.() -> Unit = {}): EditorTabsUiComponent =
  x(xpath ?: "//div[@class='EditorTabs']", EditorTabsUiComponent::class.java).apply(action)

class EditorTabsUiComponent(data: ComponentData) : UiComponent(data) {

  private val editorTabsComponent by lazy { driver.cast(component, EditorTabsRef::class) }
  val editorAndPreviewActionButton: ActionButtonUi = actionButton { byAccessibleName("Editor and Preview") }
  val selectedTabInfo: TabInfoRef? get() = editorTabsComponent.getSelectedInfo()

  fun getTabs(): List<Tab> = editorTabsComponent.getTabs().map { Tab(it) }

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

  fun rightClickTab(text: String) {
    x("//div[@class='EditorTabLabel'][.//div[@visible_text='$text']]").rightClick()
  }

  fun selectActionFromTabContextMenu(tabName: String, actionName: String, contains: Boolean = false) {
    rightClickTab(tabName)
    if (contains) {
      driver.ui.popupMenu().waitFound().selectContains(tabName)
    }
    else {
      driver.ui.popupMenu().select(actionName)
    }

  }

  /**
   *  In case of all editor tabs are missing, there can be expected IllegalStateException.
   *  Other exceptions should be processed accordingly
   */
  fun closeAllTabs() {
    val closeResult = runCatching { driver.invokeAction("CloseAllEditors") }
    closeResult.onFailure {
      if (it !is IllegalStateException) {
        throw it
      }
    }
  }

  fun isTabOpened(text: String): Boolean = getTabs().any { it.text == text }

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
