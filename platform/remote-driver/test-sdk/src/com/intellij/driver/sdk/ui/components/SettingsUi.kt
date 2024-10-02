package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.xQuery

fun WelcomeScreenUI.settingsDialog(action: SettingsUiComponent.() -> Unit): SettingsUiComponent =
  x(SettingsUiComponent::class.java) { byTitle("Settings") }.apply(action)

fun IdeaFrameUI.settingsDialog(action: SettingsUiComponent.() -> Unit): SettingsUiComponent =
  x(SettingsUiComponent::class.java) { byTitle("Settings") }.apply(action)

fun WelcomeScreenUI.showSettings() = driver.invokeAction("WelcomeScreen.Settings", now = false)

open class SettingsUiComponent(data: ComponentData): DialogUiComponent(data) {

  val settingsTree: JTreeUiComponent = tree(xQuery { byType("com.intellij.openapi.options.newEditor.SettingsTreeView${"$"}MyTree") })
  val okButton = x { byAccessibleName("OK") }

  fun content(action: UiComponent.() -> Unit): UiComponent =
    x { byType("com.intellij.openapi.options.ex.ConfigurableCardPanel") }.apply(action)
}