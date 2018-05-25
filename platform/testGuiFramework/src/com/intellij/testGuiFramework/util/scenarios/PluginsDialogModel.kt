// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util.scenarios

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.fixtures.extended.ExtendedButtonFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.util.logUIStep
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.exception.WaitTimedOutError
import javax.swing.JDialog


class PluginsDialogModel(val testCase: GuiTestCase) : TestUtilsClass(testCase) {
  companion object : TestUtilsClassCompanion<PluginsDialogModel>(
    { PluginsDialogModel(it) }
  )
}

val GuiTestCase.pluginsDialogModel: PluginsDialogModel by PluginsDialogModel

fun PluginsDialogModel.connectDialog(): JDialogFixture =
    testCase.dialog("Plugins", true, testCase.defaultTimeout)

fun PluginsDialogModel.isPluginInstalled(pluginName: String): Boolean {
  val result = try {
    with(testCase) {
      logTestStep("Search `$pluginName` plugin")
      val dialog = connectDialog()
      dialog.table(pluginName, timeout = 1L).cell(pluginName).click()
    }
    true
  }
  catch (e: Exception) {
    testCase.logInfo("No `$pluginName` plugin found")
    false
  }
  return result
}

fun PluginsDialogModel.isPluginRequiredVersionInstalled(pluginName: String, pluginVersion: String): Boolean =
    isPluginInstalled(pluginName) && getPluginVersion(pluginName).contains(pluginVersion)

fun PluginsDialogModel.getPluginVersion(pluginName: String): String {
  val version = try {
    with(testCase) {
      logTestStep("Search `$pluginName` plugin")
      val dialog = connectDialog()
      dialog.table(pluginName, timeout = 1L).cell(pluginName).click()
      logUIStep("Get installed version of `$pluginName` plugin")
      dialog.label("Version").text()?.removePrefix("version:")?.trim() ?: ""
    }
  }
  catch (e: Exception) {
    testCase.logInfo("No `$pluginName` plugin found")
    ""
  }
  testCase.logInfo("Found `$version` version of `$pluginName` plugin")
  return version
}

fun PluginsDialogModel.getPluginButton(pluginName: String, buttonName: String): ExtendedButtonFixture? {
  return try {
    with(testCase) {
      logTestStep("Search `$pluginName` plugin")
      val dialog = connectDialog()
      dialog.table(pluginName, timeout = 1L).cell(pluginName).click()
      logUIStep("Search `$buttonName` button")
      dialog.button(buttonName)
    }
  }
  catch (e: Exception) {
    testCase.logInfo("No `$buttonName` found for `$pluginName` plugin")
    null
  }
}

fun PluginsDialogModel.getUninstallButton(pluginName: String): ExtendedButtonFixture? = getPluginButton(pluginName, "Uninstall")
fun PluginsDialogModel.getUpdatesButton(pluginName: String): ExtendedButtonFixture? = getPluginButton(pluginName, "Updates")
fun PluginsDialogModel.getRestartButton(pluginName: String): ExtendedButtonFixture? = getPluginButton(pluginName, "Restart IntelliJ IDEA")

fun PluginsDialogModel.getButton(buttonName: String): ExtendedButtonFixture?{
  return with(testCase) {
    connectDialog().button(buttonName)
  }
}
fun PluginsDialogModel.pressButton(buttonName: String) {
  testCase.logUIStep("Press `$buttonName` button")
  getButton(buttonName)?.click()
}

fun PluginsDialogModel.pressOk(): Unit = pressButton("OK")
fun PluginsDialogModel.pressCancel(): Unit = pressButton("Cancel")

fun PluginsDialogModel.installPluginFromDisk(pluginFileName: String){
  with(testCase){
    logUIStep("Press `Install plugin from disk`")
    pressButton("Install plugin from disk...")
    chooseFileInFileChooser(pluginFileName)
    pressOk()
    ensureButtonOkHasPressed()
  }
}

fun PluginsDialogModel.ensureButtonOkHasPressed() {
  val dialogTitle = "Plugins"
  try {
    testCase.logTestStep("Check that `Plugins` dialog closed")
    GuiTestUtilKt.waitUntilGone(robot = testCase.robot(),
        timeoutInSeconds = 2,
        matcher = GuiTestUtilKt.typeMatcher(
            JDialog::class.java) { it.isShowing && it.title == dialogTitle })
  }
  catch (timeoutError: WaitTimedOutError) {
    with(testCase) {
      logUIStep("`Plugins` dialog not closed. Press OK again")
      getButton("OK")?.clickWhenEnabled()
    }
  }
}