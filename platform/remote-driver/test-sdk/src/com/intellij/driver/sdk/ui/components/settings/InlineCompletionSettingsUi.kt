package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.JComboBoxUiComponent
import com.intellij.driver.sdk.ui.components.elements.JRadioButtonUi

fun SettingsDialogUiComponent.inlineCompletionSettingsPage(action: InlineCompletionSettingsPageUi.() -> Unit = {}): InlineCompletionSettingsPageUi {
  openTreeSettingsSection("Editor", "General", "Code Completion", "Inline", fullMatch = false)
  return x(InlineCompletionSettingsPageUi::class.java, "Inline completion settings page") {
    byType("com.intellij.openapi.options.ex.ConfigurableCardPanel")
  }.apply(action)
}

class InlineCompletionSettingsPageUi(data: ComponentData) : UiComponent(data) {
  val enableInlineCompletionCheckBox: JCheckBoxUi =
    x(JCheckBoxUi::class.java, "'Enable inline completion using language models' checkbox") {
      byText("Enable inline completion using language models:")
    }
  val localModeRadioButton: JRadioButtonUi =
    x(JRadioButtonUi::class.java, "'Local' radio button") { byText("Local") }
  val cloudModeRadioButton: JRadioButtonUi =
    x(JRadioButtonUi::class.java, "'Cloud' radio button") { byText("Cloud") }
  val downloadModelsComboBox: JComboBoxUiComponent =
    x("//div[@text='Download models:']/following-sibling:: *[@class='ComboBox'][1]",
      JComboBoxUiComponent::class.java,
      "'Download models' combo box")
  val nextEditSuggestionsCheckBox: JCheckBoxUi =
    x(JCheckBoxUi::class.java, "'Enable next edit suggestions' checkbox") { byText("Enable next edit suggestions") }
}
