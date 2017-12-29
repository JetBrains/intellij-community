/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.testCases

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitProgressDialogUntilGone
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.fest.swing.exception.WaitTimedOutError
import java.io.File
import javax.swing.JDialog

open class PluginTestCase : GuiTestCase() {


  private val MAC_PLUGIN_HOME = "/Users/jetbrains/Documents/plugins/"
  private val WIN_PLUGIN_HOME = "C:\\WS-Plugins"
  private val LINUX_PLUGIN_HOME = "/Users/jetbrains/Documents/plugins/"

  private fun getPluginHomePath(): String {
    return when {
      SystemInfo.isMac() -> MAC_PLUGIN_HOME
      SystemInfo.isWin() -> WIN_PLUGIN_HOME
      else -> {
        LINUX_PLUGIN_HOME
      }
    }
  }

  fun findPlugin(pluginName: String): String {
    val f = File(getPluginHomePath())
    return f.listFiles { _, name ->
      name.startsWith(pluginName)
    }[0].toString()
  }

  fun installPluginAndRestart(installPluginsFunction: () -> Unit) {
    val PLUGINS_INSTALLED = "PLUGINS_INSTALLED"
    if (guiTestRule.getTestName() == GuiTestOptions.getResumeTestName() &&
        GuiTestOptions.getResumeInfo() == PLUGINS_INSTALLED) {
    }
    else {
      //if plugins are not installed yet
      installPluginsFunction()
      //send restart message and resume this test to the server
      GuiTestThread.client?.send(TransportMessage(MessageType.RESTART_IDE_AND_RESUME, PLUGINS_INSTALLED)) ?: throw Exception(
        "Unable to get the client instance to send message.")
      //wait until IDE is going to restart
      GuiTestUtilKt.waitUntil("IDE will be closed", timeoutInSeconds = 120) { false }
    }
  }

  fun installPlugins(vararg pluginNames: String) {
    welcomeFrame {
      actionLink("Configure").click()
      popupClick("Plugins")
      dialog("Plugins") {
        button("Install JetBrains plugin...").click()
        dialog("Browse JetBrains Plugins ") browsePlugins@ {
          pluginNames.forEach { this@browsePlugins.installPlugin(it) }
          button("Close").click()
        }
        button("OK")
        ensureButtonOkHasPressed(this@PluginTestCase)
      }
      message("IDE and Plugin Updates") {
        button("Postpone").click()
      }
    }
  }

  fun installPluginFromDisk(pluginPath: String, pluginName: String) {
    welcomeFrame {
      actionLink("Configure").click()
      popupClick("Plugins")
      dialog("Plugins") {
        //Check if plugin has already been installed
        try {
          table(pluginName, timeout = 1L).cell(pluginName).click()
          button("OK").click()
          ensureButtonOkHasPressed(this@PluginTestCase)
        }
        catch (e: Exception) {
          button("Install plugin from disk...").click()
          chooseFileInFileChooser(pluginPath)
          button("OK").click()
          ensureButtonOkHasPressed(this@PluginTestCase)
        }
      }
      try {
        message("IDE and Plugin Updates", timeout = 5L) {
          button("Shutdown").click()
        }
      }
      catch (e: Exception) {
      }
    }
  }

  private fun ensureButtonOkHasPressed(guiTestCase: GuiTestCase) {
    val dialogTitle = "Plugins"
    try {
      GuiTestUtilKt.waitUntilGone(robot = guiTestCase.robot(),
                                  timeoutInSeconds = 2,
                                  matcher = GuiTestUtilKt.typeMatcher(
                                    JDialog::class.java) { it.isShowing && it.title == dialogTitle })
    }
    catch (timeoutError: WaitTimedOutError) {
      with(guiTestCase) {
        val dialog = JDialogFixture.find(guiTestCase.robot(), dialogTitle)
        with(dialog) {
          button("OK").clickWhenEnabled()
        }
      }
    }
  }

  private fun JDialogFixture.installPlugin(pluginName: String) {
    table(pluginName).cell(pluginName).click()
    button("Install").click()
    waitProgressDialogUntilGone(robot(), "Downloading Plugins")
    println("$pluginName plugin has been installed")
  }

}