package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder

fun Finder.menuAndToolbarsSettings(action: MenuAndToolbarsSettingsUI.() -> Unit) {
  x("//div[@class='DialogRootPane']", MenuAndToolbarsSettingsUI::class.java).action()
}

class MenuAndToolbarsSettingsUI(data: ComponentData) : UiComponent(data) {
  val restoreActionsButton = x("//div[@accessiblename='Restore Actions…' and @class='ActionButton']")
  val okButton = x("//div[@accessiblename='OK']")
}