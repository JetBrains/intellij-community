package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent

fun SettingsDialogUiComponent.typeEngineSettingsPage(action: PyTypeEngineSettingsPageUi.() -> Unit = {}): PyTypeEngineSettingsPageUi =
  x(PyTypeEngineSettingsPageUi::class.java, "'Type Engine' settings page") { byType("com.intellij.openapi.options.ex.ConfigurableCardPanel") }.apply(action)

class PyTypeEngineSettingsPageUi(data: ComponentData) : UiComponent(data) {

  val builtInEngineButton: ActionButtonUi =
    x(ActionButtonUi::class.java, "'Built-in' type engine button") { and(byClass("SegmentedButton"), byAccessibleName("Built-in")) }

  val pyreflyEngineButton: ActionButtonUi =
    x(ActionButtonUi::class.java, "'Pyrefly' type engine button") { and(byClass("SegmentedButton"), byAccessibleName("Pyrefly")) }

  val tyEngineButton: ActionButtonUi =
    x(ActionButtonUi::class.java, "'ty' type engine button") { and(byClass("SegmentedButton"), byAccessibleName("ty")) }
}
