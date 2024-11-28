package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.should
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.seconds

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

  val settingsTree
    get() = tree("//div[@accessiblename='Settings categories']")

  fun openTreeSettingsSection(vararg path: String, fullMatch: Boolean = true) {
    settingsTree.should(message = "Settings tree is empty", timeout = 5.seconds) { collectExpandedPaths().isNotEmpty() }
    settingsTree.clickPath(*path, fullMatch = fullMatch)
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