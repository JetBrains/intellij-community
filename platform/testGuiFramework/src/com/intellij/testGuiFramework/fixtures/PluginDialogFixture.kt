// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurableNew.TabHeaderComponent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ex.ConfigurableCardPanel
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickButtonWhenEnabled
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickCancelButton
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickOkButton
import com.intellij.testGuiFramework.impl.popupClick
import com.intellij.testGuiFramework.impl.waitUntilFound
import com.intellij.ui.components.BasicOptionButtonUI.ArrowButton
import com.intellij.ui.components.JBOptionButton
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import javax.swing.*
import javax.swing.text.Position

class PluginDialogFixture(robot: Robot, pluginDialog: JDialog): JDialogFixture(robot, pluginDialog), ContainerFixture<JDialog> {
  fun isPluginInstalled(pluginId: String): Boolean = PluginManager.isPluginInstalled(PluginId.getId(pluginId))

  fun getPluginVersion(pluginId: String): String =
    PluginManager.getPlugin(PluginId.getId(pluginId))?.version ?: throw IllegalArgumentException("Plugin ${pluginId} not found")

  fun isPluginEnabled(pluginId: String): Boolean = !PluginManager.getDisabledPlugins().contains(pluginId)

  fun showInstalledPlugins() {
    val tabHeader: TabHeaderComponent = findTabHeader()
    robot().click(tabHeader, tabHeader.getTabLocation("Installed"))
  }

  fun pluginDetails(pluginName: String, func: PluginDetailsFixture.() -> Unit) {
    robot().click(findPluginDetailsLink(pluginName))
    func(PluginDetailsFixture(robot(), target()))
  }

  fun showInstallPluginFromDiskDialog() {
    val actionButton: ActionButton = waitUntilFound(findTabHeader(), ActionButton::class.java, defaultTimeout) { true }
    robot().click(actionButton)
    popupClick("Install Plugin from Disk...")
  }

  fun installPluginFromDiskDialog(func: InstallPluginFromDiskFixture.() -> Unit) {
    val installPluginFromDiskDialog: JDialog =
      waitUntilFound(target(), JDialog::class.java, defaultTimeout) { it.title == "Choose Plugin File" }
    func(InstallPluginFromDiskFixture(robot(), installPluginFromDiskDialog))
  }

  fun ok() = findAndClickOkButton(this)

  fun cancel() = findAndClickCancelButton(this)

  private fun findTabHeader(): TabHeaderComponent =
    waitUntilFound(target(), TabHeaderComponent::class.java, defaultTimeout) { true }

  private fun findPluginDetailsLink(pluginName: String): JLabel {
    val pluginsCardPanel: ConfigurableCardPanel =
      waitUntilFound(target(), ConfigurableCardPanel::class.java, defaultTimeout) { true }
    return waitUntilFound(pluginsCardPanel, JLabel::class.java, defaultTimeout) { it.text == pluginName }
  }

  class PluginDetailsFixture(robot: Robot, dialog: JDialog): JDialogFixture(robot, dialog) {

    fun disable() = robot().click(findEnableDisableButton("Disable"))

    fun enable() = robot().click(findEnableDisableButton("Enable"))

    fun uninstall() {
      val arrowButton: ArrowButton = waitUntilFound(target(), ArrowButton::class.java, defaultTimeout) { true }
      robot().click(arrowButton)

      val list : JList<*> = waitUntilFound(target(), JList::class.java, defaultTimeout) {
        it.isShowing && it.isVisible && getUninstallItemIndex(it) != -1
      }
      robot().click(list, list.indexToLocation(getUninstallItemIndex(list)))
    }

    fun back() {
      val backButton: JButton = waitUntilFound(target(), JButton::class.java, defaultTimeout) { it.text == "Plugins" }
      robot().click(backButton)
    }

    private fun findEnableDisableButton(text: String): JButton =
      waitUntilFound(target(), JButton::class.java, defaultTimeout) { it !is JBOptionButton && it.text == text }

    private fun getUninstallItemIndex(list: JList<*>): Int = list.getNextMatch("Uninstall", 0, Position.Bias.Forward)
  }

  class InstallPluginFromDiskFixture(robot: Robot, installPluginFromDiskDialog: JDialog): JDialogFixture(robot, installPluginFromDiskDialog),
                                                                                          ContainerFixture<JDialog> {
    fun setPath(pluginPath: String) {
      val pluginPathTextField: JTextField =
        waitUntilFound(target(), JTextField::class.java, defaultTimeout) { true }
      pluginPathTextField.text = pluginPath
    }

    fun install() = findAndClickButtonWhenEnabled(this, "OK")

    fun cancel() = findAndClickButtonWhenEnabled(this, "Cancel")
  }
}