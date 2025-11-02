package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

fun Finder.openKotlinNotebookSettingsPanel() {
  notebookEditor {
    kotlinNotebookToolbar.openSettingsButton.click()
  }
}

/**
 * Opens a Kotlin Notebook Settings panel and applies [action] to it with autoclose
 */
fun Finder.kotlinNotebookSettingsFromToolbar(action: KotlinNotebookUiSettingsComponent.() -> Unit) {
  openKotlinNotebookSettingsPanel()
  withKotlinNotebookSettingsPanel {
    action()
  }
}

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