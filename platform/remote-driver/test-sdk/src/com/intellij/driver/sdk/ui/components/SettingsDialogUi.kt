package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun Finder.settingsDialog(@Language("xpath") xpath: String? = null): SettingsDialogUiComponent {
  return x(xpath ?: "//div[@class='MyDialog' and @accessiblename='Settings']", SettingsDialogUiComponent::class.java)
}

@Internal
class SettingsDialogUiComponent(data: ComponentData) : UiComponent(data) {
  val themeComboBox
    get() = comboBox("//div[@accessiblename='Theme:' and @class='ComboBox']")

  val pluginsPanel
    get() = x("//div[@class='ConfigurableEditor']")

  val searchPluginTextField
    get() = pluginsPanel.textField("//div[@class='TextFieldWithProcessing']")

  val installedTab
    get() = x("//div[@class='JLabel' and @text ='Installed']")

  val checkBoxTree
    get() = x("//div[@class='CheckboxTree']", JCheckboxTreeFixture::class.java)

  fun openTreeSettingsSection(vararg path: String, fullMatch: Boolean = true) {
    tree("//div[@accessiblename='Settings categories']").clickPath(*path, fullMatch=fullMatch)
  }

  fun installPluginFromList(pluginName: String) {
    x("//div[@class='ListPluginComponent'][./div[@text='$pluginName']]", ListPluginComponent::class.java)
      .waitFound()
      .installButton
      .click()
  }

  val pluginDetailsPage: PluginDetailsPage
    get() = x("//div[@class='PluginDetailsPageComponent']", PluginDetailsPage::class.java)
}

internal class ListPluginComponent(data: ComponentData) : UiComponent(data) {
  val installButton: UiComponent
    get() = x("//div[@class='InstallButton']")
}

@Internal
class PluginDetailsPage(data: ComponentData) : UiComponent(data) {
  val optionButton: UiComponent
    get() = x("//div[@class='OptionButton']")
}