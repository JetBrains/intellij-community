package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent

fun SettingsDialogUiComponent.packageRepositoriesSettingsPage(action: PackageRepositoriesSettingsPageUi.() -> Unit = {}): PackageRepositoriesSettingsPageUi {
  openTreeSettingsSection("Python", "Package Repositories", fullMatch = false)
  return x(PackageRepositoriesSettingsPageUi::class.java) {
    byType("com.intellij.openapi.options.ex.ConfigurableCardPanel")
  }.apply(action)
}

class PackageRepositoriesSettingsPageUi(data: ComponentData) : UiComponent(data) {
  val addButton = x { and(byClass("ActionButton"), byAccessibleName("Add")) }
  val removeButton = x { and(byClass("ActionButton"), byAccessibleName("Delete")) }
  val enableRepositoryCheckBox = x { byAccessibleName("Enable repository") }

  val nameField = x("//div[@accessiblename='Name:' and @class='JBTextField']")
  val urlField = x("//div[@accessiblename='Repository URL:' and @class='JBTextField']")
}
