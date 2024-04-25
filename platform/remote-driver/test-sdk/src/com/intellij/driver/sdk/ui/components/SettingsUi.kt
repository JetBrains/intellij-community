package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Locators

fun WelcomeScreenUI.settingsDialog(action: SettingsUiComponent.() -> Unit): SettingsUiComponent =
  x(Locators.byTitle("Settings"), SettingsUiComponent::class.java).apply(action)

fun IdeaFrameUI.settingsDialog(action: SettingsUiComponent.() -> Unit): SettingsUiComponent =
  x(Locators.byTitle("Settings"), SettingsUiComponent::class.java).apply(action)

fun WelcomeScreenUI.showSettings() = driver.invokeAction("WelcomeScreen.Settings", now = false)

fun IdeaFrameUI.showSettings() = driver.invokeAction("ShowSettings", now = false)

class SettingsUiComponent(data: ComponentData): UiComponent(data) {

  val settingsTree: JTreeUiComponent = tree(Locators.byType("com.intellij.openapi.options.newEditor.SettingsTreeView${"$"}MyTree"))
  val okButton = x(Locators.byAccessibleName("OK"))

  fun content(action: UiComponent.() -> Unit): UiComponent =
    x(Locators.byType("com.intellij.openapi.options.ex.ConfigurableCardPanel")).apply(action)
}