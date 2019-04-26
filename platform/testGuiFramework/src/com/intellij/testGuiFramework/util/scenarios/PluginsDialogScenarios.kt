// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.RestartIdeAndResumeContainer
import com.intellij.testGuiFramework.remote.transport.RestartIdeCause
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.step
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
    pluginDialog {
      pluginDetails(pluginName) {
        if (isPluginInstalled()) {
          step("uninstall `$pluginName` plugin") {
            uninstall()
          }
        }
      }
      ok()
    }
    dialog("IDE and Plugin Updates", timeout = Timeouts.seconds05) { button("Postpone").click() }
  }
}

fun PluginsDialogScenarios.actionAndRestart(actionFunction: () -> Unit) {
  val PLUGINS_INSTALLED = "PLUGINS_INSTALLED"
  if (testCase.guiTestRule.getTestName() == GuiTestOptions.resumeTestName &&
      GuiTestOptions.resumeInfo == PLUGINS_INSTALLED) {
    testCase.logInfo("Restart succeeded")
  }
  else {
    //if plugins are not installed yet
    actionFunction()
    step("restart IDE") {
      //send restart message and resume this test to the server
      GuiTestThread.client?.send(TransportMessage(MessageType.RESTART_IDE_AND_RESUME, RestartIdeAndResumeContainer(
        RestartIdeCause.PLUGIN_INSTALLED))) ?: throw Exception(
        "Unable to get the client instance to send message.")
      //wait until IDE is going to restart
      GuiTestUtilKt.waitUntil("IDE will be closed", timeout = Timeouts.defaultTimeout) { false }
    }
  }
}

fun PluginsDialogScenarios.installPluginFromDisk(pluginFileName: String) {
  with(testCase) {
    welcomePageDialogModel.openPluginsDialog()
    pluginDialog {
      showInstallPluginFromDiskDialog()
      installPluginFromDiskDialog {
        setPath(pluginFileName)
        clickOk()
      }
      ok()
    }
    if (isMac) {
      dialogWithTextComponent(Timeouts.seconds05, { it.text.contains("IDE and Plugin Updates") }) { button("Postpone").click() }
    } else {
      dialog("IDE and Plugin Updates", timeout = Timeouts.seconds05) { button("Postpone").click() }
    }
  }
}

fun PluginsDialogScenarios.isPluginRequiredVersionInstalled(pluginName: String, pluginVersion: String): Boolean {
  var version = ""
  with(testCase) {
    welcomePageDialogModel.openPluginsDialog()
    pluginDialog {
      showInstalledPlugins()
      cancel()
    }
    welcomePageDialogModel.openPluginsDialog()
    step("get version of `$pluginName` plugin") {
      pluginDialog {
        if (isPluginInstalled(pluginName)) { // it can be shown on trending page and not installed
          pluginDetails(pluginName) {
            if (isPluginInstalled(pluginName)) {
              version = pluginVersion()
              logInfo("Found `$version` version of `$pluginName` plugin")
            }
          }
        }
        else
          logInfo("No `$pluginName` plugin")
        cancel()
      }
    }
  }
  return version == pluginVersion
}
