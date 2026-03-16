package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent

fun Finder.withKotlinNotebookSettingsDialog(action: KotlinNotebookUiSettingsComponent.() -> Unit) {
  notebookEditor {
    kotlinNotebookToolbar.openSettingsButton.click()
  }
  x(KotlinNotebookSettingsDialog::class.java) {
    byTitle("Settings")
  }.run {
    try {
      settingsPanel.action()
    }
    finally {
      okButton.click()
    }
  }
}

class KotlinNotebookSettingsDialog(data: ComponentData) : DialogUiComponent(data) {
  val settingsPanel: KotlinNotebookUiSettingsComponent
    get() = x( KotlinNotebookUiSettingsComponent::class.java) {
      byClass("LoadingDecoratorLayeredPaneImpl")
    }
}


class KotlinNotebookUiSettingsComponent(data: ComponentData) : UiComponent(data) {
  val showSessionVariablesCheckBox: UiComponent
    get() = x("""
      //div[@class='JBCheckBox' and contains(@text, 'session variables')]
    """.trimIndent())

  val focusOnVariablesCheckBox: UiComponent
    get() = x("""
      //div[@class='JBCheckBox' and contains(@text, 'after cell execution')]
    """.trimIndent())
}