package com.intellij.driver.sdk.ui.components.ultimate

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.comboBox

fun IdeaFrameUI.newDataSourceDialog(action: NewDataSourceDialogUi.() -> Unit = {}): NewDataSourceDialogUi =
  x(NewDataSourceDialogUi::class.java) { byTitle("New Data Source") }.apply(action)

class NewDataSourceDialogUi(data: ComponentData) : DialogUiComponent(data) {

  fun selectDriver(driverName: String) {
    comboBox("//div[@accessiblename='Driver:']//div[@class='JComboBox']").selectItemContains(driverName)
  }
}
