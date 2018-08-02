// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

class PluginsDialogScenarios(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<PluginsDialogScenarios>(
    { PluginsDialogScenarios(it) }
  )
}

val GuiTestCase.pluginsDialogScenarios: PluginsDialogScenarios by PluginsDialogScenarios

fun PluginsDialogScenarios.uninstallPlugin(pluginName: String) {
  with(testCase) {
    welcomePageDialogModel.openPluginsDialog()
    if (pluginsDialogModel.isPluginInstalled(pluginName)) {
      val uninstallButton = pluginsDialogModel.getUninstallButton(pluginName)
      if (uninstallButton != null) {
        logTestStep("Uninstall `$pluginName` plugin")
        uninstallButton.click()
        pluginsDialogModel.pressOk()
      }
      else {
        pluginsDialogModel.pressCancel()
      }
      dialog("IDE and Plugin Updates", timeout = 5L) { button("Postpone").click() }
    }
    else
      pluginsDialogModel.pressCancel()
  }
}

fun PluginsDialogScenarios.actionAndRestart(actionFunction: () -> Unit) {
  val PLUGINS_INSTALLED = "PLUGINS_INSTALLED"
  if (testCase.guiTestRule.getTestName() == GuiTestOptions.getResumeTestName() &&
      GuiTestOptions.getResumeInfo() == PLUGINS_INSTALLED) {
    testCase.logInfo("Restart succeeded")
  }
  else {
    //if plugins are not installed yet
    actionFunction()
    testCase.logTestStep("Restart IDE")
    //send restart message and resume this test to the server
    GuiTestThread.client?.send(TransportMessage(MessageType.RESTART_IDE_AND_RESUME, PLUGINS_INSTALLED)) ?: throw Exception(
      "Unable to get the client instance to send message.")
    //wait until IDE is going to restart
    GuiTestUtilKt.waitUntil("IDE will be closed", timeoutInSeconds = 120) { false }
  }
}

fun PluginsDialogScenarios.installPluginFromDisk(pluginFileName: String) {
  with(testCase) {
    welcomePageDialogModel.openPluginsDialog()
    pluginsDialogModel.installPluginFromDisk(pluginFileName)
    dialog("IDE and Plugin Updates", timeout = 5L) { button("Postpone").click() }
  }
}

fun PluginsDialogScenarios.isPluginRequiredVersionInstalled(pluginName: String, pluginVersion: String): Boolean {
  var result = false
  with(testCase) {
    welcomePageDialogModel.openPluginsDialog()
    result = pluginsDialogModel.isPluginRequiredVersionInstalled(pluginName, pluginVersion)
    pluginsDialogModel.pressCancel()
  }
  return result
}
