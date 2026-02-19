package com.intellij.driver.sdk.ui.components.notebooks

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.withRetries

/**
 * Represents the UI component for Kotlin-specific toolbar actions
 */
class KotlinNotebookActionToolBarComponent(data: ComponentData) : UiComponent(data) {
  val dependenciesSelector: ComboBoxActionButton
    get() = x(
      xpath = "//div[@class='ActionButtonWithText' and contains(@javaclass, 'KotlinNotebookDependenciesComboBox')]",
      type = ComboBoxActionButton::class.java,
    )

  val runModeSelector: RunModeSelectorComboBox
    get() = x(
      xpath = "//div[@class='ActionButtonWithText' and contains(@javaclass, 'KotlinNotebookSessionModeComboBox')]",
      type = RunModeSelectorComboBox::class.java
    )

  val openSettingsButton: UiComponent
    get() = x("//div[@myicon='settings.svg']")
}

class ComboBoxActionButton(data: ComponentData) : UiComponent(data) {
  fun selectItem(itemText: String) {
    // it looks like due to some race condition,
    // sometimes the combobox is not updated after the mode change, so we need to retry a few times
    val attemptCount = 5
    withRetries(times = attemptCount) {
      if (getAllTexts().any { it.text == itemText }) return@withRetries
      click()
      val dropdown = driver.ui.ideFrame().list { byClass("MyList") }
      dropdown.clickItem(itemText = itemText)
    }

    waitOneText(itemText, "Failed to select item '$itemText' after $attemptCount attempts")
  }
}
