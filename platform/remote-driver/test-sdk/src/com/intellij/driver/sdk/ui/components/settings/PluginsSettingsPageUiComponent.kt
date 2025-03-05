package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.textField

fun SettingsDialogUiComponent.pluginsSettingsPage(action: PluginsSettingsPageUiComponent.() -> Unit = {}): PluginsSettingsPageUiComponent =
  x("//div[@class='ConfigurableEditor']/ancestor::div[.//div[@accessiblename='Installed']][1]", PluginsSettingsPageUiComponent::class.java).apply(action)

class PluginsSettingsPageUiComponent(data: ComponentData) : UiComponent(data) {
  val searchPluginTextField = textField { byAccessibleName("Search plugins") }
  val installedTab = x { byAccessibleName("Installed") }

  fun listPluginComponent(pluginName: String, action: ListPluginComponent.() -> Unit = {}): ListPluginComponent =
    x(ListPluginComponent::class.java) {
      and(byType("com.intellij.ide.plugins.newui.ListPluginComponent"), byAccessibleName(pluginName))
    }.apply(action)

  fun pluginDetailsPage(action: PluginDetailsPage.() -> Unit = {}): PluginDetailsPage =
    x(PluginDetailsPage::class.java) { byType("com.intellij.ide.plugins.newui.PluginDetailsPageComponent") }.apply(action)

  class ListPluginComponent(data: ComponentData) : UiComponent(data) {
    val installButton = x { byType("com.intellij.ide.plugins.newui.InstallButton") }
  }

  class PluginDetailsPage(data: ComponentData) : UiComponent(data) {
    val optionButton = x { byType("com.intellij.ide.plugins.newui.SelectionBasedPluginModelAction\$OptionButton") }
  }
}