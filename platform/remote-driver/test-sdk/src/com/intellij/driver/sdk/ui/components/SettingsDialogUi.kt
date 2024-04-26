package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language

fun Finder.settingsDialog(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyDialog' and @accessiblename='Settings']", SettingsDialogUiComponent::class.java)

class SettingsDialogUiComponent(data: ComponentData) : UiComponent(data) {

  val themeComboBox
    get() = comboBox("//div[@accessiblename='Theme:' and @class='ComboBox']")

  val pluginsPanel
    get() = x("//div[@class='ConfigurableEditor']")

  val searchPluginTextField
    get() = pluginsPanel.textField("//div[@class='TextFieldWithProcessing']")

  fun openTreeSettingsSection(vararg path: String) {
    tree().clickPath(*path)
  }

  fun installPluginFromList(pluginName: String) {
    x("//div[@class='ListPluginComponent'][./div[@text='$pluginName']]", ListPluginComponent::class.java)
      .installButton.click()
  }

  val pluginDetailsPage
    get() = x("//div[@class='PluginDetailsPageComponent']", PluginDetailsPage::class.java)
}

class ListPluginComponent(data: ComponentData) : UiComponent(data) {

  val installButton
    get() = x("//div[@class='InstallButton']")
}

class PluginDetailsPage(data: ComponentData) : UiComponent(data) {

  val optionButton
    get() = x("//div[@class='OptionButton']")
}