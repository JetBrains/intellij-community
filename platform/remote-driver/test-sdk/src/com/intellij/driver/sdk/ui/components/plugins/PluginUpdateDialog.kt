package com.intellij.driver.sdk.ui.components.plugins

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.table

fun Finder.choosePluginsToInstallDialog(action: ChoosePluginsToInstallDialogUi.() -> Unit = {}) =
  x(ChoosePluginsToInstallDialogUi::class.java) { byTitle("Choose Plugins to Install or Enable") }.apply(action)

class ChoosePluginsToInstallDialogUi(data: ComponentData) : DialogUiComponent(data) {
  private val pluginsTable: JTableUiComponent
    get() = table()

  fun selectPlugin(pluginName: String) {
    pluginsTable.apply {
      val (pluginRow, _) = findRowColumn { it.contains(pluginName) }
      clickCell(pluginRow, 0)
    }
  }

  fun installAndClose() {
    okButton.click()
  }
}

fun Finder.pluginUpdatesDialog(action: PluginUpdatesDialogUi.() -> Unit = {}) =
  x(PluginUpdatesDialogUi::class.java) { byTitle("Plugin Updates") }.apply(action)

class PluginUpdatesDialogUi(data: ComponentData) : DialogUiComponent(data) {
  private val pluginsTable: JTableUiComponent
    get() = table()

  private val updateButton = x { byVisibleText("Update") }

  fun updatePlugin() {
    updateButton.click()
  }
}