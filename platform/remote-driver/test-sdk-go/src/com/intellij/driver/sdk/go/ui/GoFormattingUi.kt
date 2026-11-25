package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import com.intellij.driver.sdk.ui.xQuery

internal fun SettingsDialogUiComponent.openGoCodeStyleOtherTab() {
  x { and(byClass("TabLabel"), byAccessibleName("Other")) }.click()
}

internal fun SettingsDialogUiComponent.gofmtOnReformatCheckBox(): JCheckBoxUi =
  checkBox(xQuery { byAccessibleName("On Reformat Code action") })
