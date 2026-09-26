package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent

fun SettingsDialogUiComponent.packageRepositoriesSettingsPage(action: PackageRepositoriesSettingsPageUi.() -> Unit = {}): PackageRepositoriesSettingsPageUi {
  openTreeSettingsSection("Python", "Package Repositories", fullMatch = false)
  return x(PackageRepositoriesSettingsPageUi::class.java, "Package Repositories settings page") {
    byType("com.intellij.openapi.options.ex.ConfigurableCardPanel")
  }.apply(action)
}

class PackageRepositoriesSettingsPageUi(data: ComponentData) : UiComponent(data) {
  val addButton = x("'Add repository' button") { and(byClass("ActionButton"), byAccessibleName("Add")) }
  val removeButton = x("'Delete repository' button") { and(byClass("ActionButton"), byAccessibleName("Delete")) }
  val enableRepositoryCheckBox = x("'Enable repository' checkbox") { byAccessibleName("Enable repository") }

  val nameField = x("//div[@accessiblename='Name:' and @class='JBTextField']", "'Name' field")
  val urlField = x("//div[@accessiblename='Repository URL:' and @class='JBTextField']", "'Repository URL' field")
}
