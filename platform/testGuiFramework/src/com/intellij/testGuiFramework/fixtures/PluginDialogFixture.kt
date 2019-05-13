// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.newui.CellPluginComponent
import com.intellij.ide.plugins.newui.TabHeaderComponent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileChooser.actions.RefreshFileChooserAction
import com.intellij.openapi.options.ex.ConfigurableCardPanel
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickButtonWhenEnabled
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickCancelButton
import com.intellij.testGuiFramework.framework.GuiTestUtil.findAndClickOkButton
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.util.waitFor
import com.intellij.ui.components.BasicOptionButtonUI.ArrowButton
import com.intellij.ui.components.JBOptionButton
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JTextComponentFixture
import javax.swing.*
import javax.swing.text.Position

class PluginDialogFixture(robot: Robot, pluginDialog: JDialog) : JDialogFixture(robot, pluginDialog), ContainerFixture<JDialog> {
  fun isPluginInstalled(pluginName: String): Boolean =
    step("check whether plugin '$pluginName' installed") {
      val result = findPluginsAppearedOnTheScreen().find { it.name == pluginName } != null
      logInfo("plugin '$pluginName' is ${if(result) "" else "NOT"}installed")
      return@step result
    }

  fun isPluginEnabled(pluginName: String): Boolean = findCheckBox(pluginName).isSelected

  fun enablePlugin(pluginName: String) {
    if (!isPluginEnabled(pluginName)) robot().click(findCheckBox(pluginName))
  }

  fun disablePlugin(pluginName: String) {
    if (isPluginEnabled(pluginName)) robot().click(findCheckBox(pluginName))
  }

  fun showInstalledPlugins() {
    step("show 'Installed' tab") {
      val tabHeader: TabHeaderComponent = findTabHeader()
      robot().click(tabHeader, tabHeader.getTabLocation("Installed"))
    }
  }

  fun pluginDetails(pluginName: String, func: PluginDetailsFixture.() -> Unit) {
    robot().click(findPluginDetailsLink(pluginName))
    func(PluginDetailsFixture(robot(), target()))
  }

  fun showInstallPluginFromDiskDialog() {
    step("call 'Install Plugin from Disk dialog'") {
      val actionButton: ActionButton = waitUntilFound(findTabHeader(), ActionButton::class.java, Timeouts.defaultTimeout) { true }
      robot().click(actionButton)
      popupMenu("Install Plugin from Disk...").clickSearchedItem()
    }
  }

  fun installPluginFromDiskDialog(func: InstallPluginFromDiskFixture.() -> Unit) {
    step("install plugin from disk") {
      val installPluginFromDiskDialog: JDialog =
        waitUntilFound(target(), JDialog::class.java, Timeouts.defaultTimeout) { it.title == "Choose Plugin File" }
      func(InstallPluginFromDiskFixture(robot(), installPluginFromDiskDialog))
    }
  }

  fun ok() = findAndClickOkButton(this)

  fun cancel() = findAndClickCancelButton(this)

  fun findPluginsAppearedOnTheScreen(): Iterable<IdeaPluginDescriptor> =
    waitUntilFoundList(findPluginCardsPanel(), CellPluginComponent::class.java,
                       Timeouts.defaultTimeout) { it.isShowing }.map { it.pluginDescriptor }

  private fun findCheckBox(pluginName: String) =
    waitUntilFound(findCellPluginComponent(pluginName), JCheckBox::class.java, Timeouts.defaultTimeout) { true }

  private fun findTabHeader(): TabHeaderComponent =
    waitUntilFound(target(), TabHeaderComponent::class.java, Timeouts.defaultTimeout) { true }

  private fun findPluginCardsPanel(): ConfigurableCardPanel =
    waitUntilFound(target(), ConfigurableCardPanel::class.java, Timeouts.defaultTimeout) { true }

  private fun findCellPluginComponent(pluginName: String): CellPluginComponent =
    waitUntilFound(findPluginCardsPanel(), CellPluginComponent::class.java,
                   Timeouts.defaultTimeout) { it.isShowing && it.pluginDescriptor.name == pluginName }

  private fun findPluginDetailsLink(pluginName: String): JLabel =
    waitUntilFound(findCellPluginComponent(pluginName), JLabel::class.java, Timeouts.defaultTimeout) { it.text == pluginName }

  class PluginDetailsFixture(robot: Robot, dialog: JDialog) : JDialogFixture(robot, dialog) {

    fun pluginVersion(): String =
      waitUntilFound(target(), JTextField::class.java, Timeouts.defaultTimeout) { it.text.startsWith("v") || it.text == "bundled" }.text

    fun isPluginEnabled(): Boolean = findEnableDisableButton().text == "Disable"

    fun isPluginInstalled(): Boolean {
      val enableDisableButtonsCount: Int = robot().finder().findAll(GuiTestUtilKt.typeMatcher(JButton::class.java) {
        it !is JBOptionButton && (it.text == "Disable" || it.text == "Enable")
      }).size
      return when (enableDisableButtonsCount) {
        0 -> false
        1 -> true
        else -> throw ComponentLookupException("Found more than one enable-disable button")
      }
    }

    fun disable() {
      val enableDisableButton: JButton = findEnableDisableButton()
      if (enableDisableButton.text == "Disable") robot().click(enableDisableButton)
    }

    fun enable() {
      val enableDisableButton: JButton = findEnableDisableButton()
      if (enableDisableButton.text == "Enable") robot().click(enableDisableButton)
    }

    fun uninstall() {
      val arrowButton: ArrowButton = waitUntilFound(target(), ArrowButton::class.java, Timeouts.defaultTimeout) { true }
      robot().click(arrowButton)

      val list: JList<*> = waitUntilFound(target(), JList::class.java, Timeouts.defaultTimeout) {
        it.isShowing && it.isVisible && getUninstallItemIndex(it) != -1
      }
      robot().click(list, list.indexToLocation(getUninstallItemIndex(list)))
    }

    fun back() {
      val backButton: JButton = waitUntilFound(target(), JButton::class.java, Timeouts.defaultTimeout) { it.text == "Plugins" }
      robot().click(backButton)
    }

    private fun findEnableDisableButton(): JButton =
      waitUntilFound(target(), JButton::class.java,
                     Timeouts.defaultTimeout) { it !is JBOptionButton && (it.text == "Enable" || it.text == "Disable") }

    private fun getUninstallItemIndex(list: JList<*>): Int = list.getNextMatch("Uninstall", 0, Position.Bias.Forward)
  }

  class InstallPluginFromDiskFixture(robot: Robot, installPluginFromDiskDialog: JDialog) : JDialogFixture(robot,
                                                                                                          installPluginFromDiskDialog),
                                                                                           ContainerFixture<JDialog> {
    fun setPath(pluginPath: String) {
      step("specify path where installed plugin is taken from") {
        waitFor {
          val pluginPathTextField: JTextField =
            waitUntilFound(target(), JTextField::class.java, Timeouts.defaultTimeout) { it.isEnabled && it.isShowing }
          clickRefresh()
          JTextComponentFixture(robot(), pluginPathTextField).deleteText().enterText(pluginPath)
          pluginPathTextField.text == pluginPath
        }
      }
    }

    fun clickRefresh() = actionButtonByClass(RefreshFileChooserAction::class.java.simpleName).click()

    fun clickOk() = findAndClickButtonWhenEnabled(this, "OK")

    fun clickCancel() = findAndClickButtonWhenEnabled(this, "Cancel")
  }
}
