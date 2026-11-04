package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.textFieldWithBrowseButton
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.IdeBundle

/**
 * Settings | Appearance & Behavior | Trusted Locations page, see `com.intellij.ide.impl.TrustedHostsConfigurable`.
 * The page has no class of its own, so the lookup takes the settings content with the label of the page.
 */
fun SettingsDialogUiComponent.trustedLocationsSettingsPage(action: TrustedLocationsSettingsPageUi.() -> Unit = {}): TrustedLocationsSettingsPageUi =
  x(TrustedLocationsSettingsPageUi::class.java, "Trusted Locations settings page") {
    componentWithChild(byType("com.intellij.openapi.options.ex.ConfigurableCardPanel"),
                       byAccessibleName(IdeBundle.message("trusted.folders.settings.label")))
  }.apply(action)

class TrustedLocationsSettingsPageUi(data: ComponentData) : UiComponent(data) {
  val locationsList: JListUiComponent = list { byType("com.intellij.ui.components.JBList") }
  val addButton: UiComponent = x("'Add' button") {
    and(byType("com.intellij.openapi.actionSystem.impl.ActionButton"), byAccessibleName("Add"))
  }

  val removeButton: UiComponent = x("'Remove' button") {
    and(byType("com.intellij.openapi.actionSystem.impl.ActionButton"), byAccessibleName("Remove"))
  }

  /** The change is applied by OK or Apply of Settings. */
  fun removeLocation(path: String) {
    locationsList.clickItem(path)
    removeButton.click()
  }

  /** The change is applied by OK or Apply of Settings. */
  fun addLocation(path: String) {
    addButton.click()
    driver.ui.dialog(title = "New Trusted Location") {
      textFieldWithBrowseButton().text = path
      okButton.click()
    }
  }
}
