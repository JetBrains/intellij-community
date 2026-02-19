package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.menuAndToolbarsSettings(action: MenuAndToolbarsSettingsUI.() -> Unit) {
  x("//div[@class='DialogRootPane']", MenuAndToolbarsSettingsUI::class.java).action()
}

class MenuAndToolbarsSettingsUI(data: ComponentData) : UiComponent(data) {
  val restoreActionsButton = x("//div[@accessiblename='Restore Actions…' and @class='ActionButton']")
  val okButton = x("//div[@accessiblename='OK']")
}