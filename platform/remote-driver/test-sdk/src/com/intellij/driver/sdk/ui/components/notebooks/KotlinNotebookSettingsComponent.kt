package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.wait
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

@Language("XPath")
private const val KOTLIN_NOTEBOOK_SETTINGS_PANEL_XPATH = """
    //div[@class='SettingsEditor']
    //div[@class='JPanel' and .//div[contains(@class, 'Breadcrumbs') and contains(@visible_text, 'Kotlin Notebook')]]
"""

fun Finder.withKotlinNotebookSettingsPanel(action: KotlinNotebookUiSettingsComponent.() -> Unit) {
  with(kotlinNotebookSettingsPanelComponent) {
    try {
      action()
      wait(1.seconds)
    } finally {
      applyAndClose()
    }
  }
}

/**
 * Accessor for Kotlin Notebook Settings Panel.
 * NB: should be invoked under [ideFrame]
 */
val Finder.kotlinNotebookSettingsPanelComponent: KotlinNotebookUiSettingsComponent
  get() = x("""
    $KOTLIN_NOTEBOOK_SETTINGS_PANEL_XPATH
    /div[@class='LoadingDecoratorLayeredPaneImpl']
  """.trimIndent(), KotlinNotebookUiSettingsComponent::class.java)


class KotlinNotebookUiSettingsComponent(data: ComponentData) : UiComponent(data) {
  fun <T> withSettingsEditorPanel(action: UiComponent.() -> T): T {
    val settingsEditorPanel = findInParentContext("""
      //div[@class='MyDialog' and @title='Settings']
      //div[@class='JPanel']
    """.trimIndent())!!
    return settingsEditorPanel.action()
  }

  val showSessionVariablesCheckBox: UiComponent
    get() = x("""
      //div[@class='JBCheckBox' and contains(@text, 'session variables')]
    """.trimIndent())

  val focusOnVariablesCheckBox: UiComponent
    get() = x("""
      //div[@class='JBCheckBox' and contains(@text, 'after cell execution')]
    """.trimIndent())

  /**
   * NB: these action buttons are related to Settings Editor and should be accessed via [withSettingsEditorPanel]
   */
  val okButton: UiComponent
    get() = withSettingsEditorPanel {
      x("//div[@class='JButton' and contains(@visible_text, 'OK')]")
    }

  val applyButton: UiComponent
    get() = withSettingsEditorPanel {
      x("//div[@class='JButton' and @visible_text='Apply']")
    }

  val cancelButton: UiComponent
    get() = withSettingsEditorPanel {
      x("//div[@class='JButton' and contains(@visible_text, 'Cancel')]")
    }

  fun applyAndClose() {
    okButton.click()
  }
}