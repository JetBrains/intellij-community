package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

/**
 * Represents UI-component for Kotlin-specific toolbar actions
 */
class KotlinNotebookActionToolBarComponent(data: ComponentData) : UiComponent(data) {
  val dependenciesSelector: UiComponent
    get() = x("//div[@class='ActionButtonWithText' and contains(@javaclass, 'KotlinNotebookDependenciesComboBox')]")

  val runModeSelector: RunModeSelectorComboBoxUiComponent
    get() = x(
      "//div[@class='ActionButtonWithText' and contains(@javaclass, 'KotlinNotebookSessionModeComboBox')]",
      RunModeSelectorComboBoxUiComponent::class.java
    )

  val openSettingsButton: UiComponent
    get() = x("//div[@myicon='settings.svg']")
}