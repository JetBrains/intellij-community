package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.comboBox
import com.intellij.driver.sdk.ui.components.elements.table
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import com.intellij.driver.sdk.ui.xQuery

fun SettingsDialogUiComponent.executeGolangciLintCheckBox(): JCheckBoxUi =
  checkBox(xQuery { byAccessibleName("Execute 'golangci-lint run'") })

fun SettingsDialogUiComponent.golangciLintExecutable(): JComboBoxUiComponent =
  comboBox { and(byClass("ComboBox"), byAccessibleName("Executable:")) }

fun SettingsDialogUiComponent.golangciLintTable(): JTableUiComponent =
  table { byClass("LinterTable") }
