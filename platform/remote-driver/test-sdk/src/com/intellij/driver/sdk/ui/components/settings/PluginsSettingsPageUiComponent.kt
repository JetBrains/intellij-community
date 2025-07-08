package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.textField
import javax.swing.JButton
import javax.swing.JCheckBox

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
    val installButton = x { and(byType(JButton::class.java), byAccessibleName("Install")) }
    val installedButton = x { and(byType(JButton::class.java), byAccessibleName("Installed")) }
    val enabledCheckBox = checkBox { and(byType(JCheckBox::class.java), byAccessibleName("Enabled")) }
    val ultimateTagLabel = x { and(byType("com.intellij.ide.plugins.newui.TagComponent"), byAccessibleName("Ultimate")) }
  }

  class PluginDetailsPage(data: ComponentData) : UiComponent(data) {
    val optionButton = x { byType("com.intellij.ide.plugins.newui.SelectionBasedPluginModelAction${"$"}OptionButton") }
    val installButton = x { and(byType(JButton::class.java), byAccessibleName("Install")) }
    val uninstallButton = x { and(byType(JButton::class.java), byAccessibleName("Uninstall")) }
    val installedButton = x { and(byType(JButton::class.java), byAccessibleName("Installed")) }
    val disableButton = x { and(byType(JButton::class.java), byAccessibleName("Disable")) }
    val enableButton = x { and(byType(JButton::class.java), byAccessibleName("Enable")) }
  }
}