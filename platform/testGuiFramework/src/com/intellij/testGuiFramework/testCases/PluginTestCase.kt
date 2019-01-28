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
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.framework.Timeouts.seconds05
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.ignoreComponentLookupException
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitProgressDialogUntilGone
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.RestartIdeAndResumeContainer
import com.intellij.testGuiFramework.remote.transport.RestartIdeCause
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import com.intellij.testGuiFramework.util.step
import org.fest.swing.exception.WaitTimedOutError
import java.io.File
import javax.swing.JDialog

open class PluginTestCase : GuiTestCase() {

  val getEnv: String by lazy {
    File(System.getenv("WEBSTORM_PLUGINS") ?: throw IllegalArgumentException("WEBSTORM_PLUGINS env variable isn't specified")).absolutePath
  }

  fun findPlugin(pluginName: String): String {
    val f = File(getEnv)
    return f.listFiles { _, name ->
      name.startsWith(pluginName)
    }[0].toString()
  }

  fun installPluginAndRestart(installPluginsFunction: () -> Unit) {
    if (guiTestRule.getTestName() == GuiTestOptions.resumeTestName &&
        GuiTestOptions.resumeInfo == PLUGINS_INSTALLED) {
    }
    else {
      //if plugins are not installed yet
      installPluginsFunction()
      //send restart message and resume this test to the server
      GuiTestThread.client?.send(TransportMessage(MessageType.RESTART_IDE_AND_RESUME, RestartIdeAndResumeContainer(RestartIdeCause.PLUGIN_INSTALLED))) ?: throw Exception(
        "Unable to get the client instance to send message.")
      //wait until IDE is going to restart
      GuiTestUtilKt.waitUntil("IDE will be closed", timeout = Timeouts.defaultTimeout) { false }
    }
  }

  fun installPlugins(vararg pluginNames: String) {
    welcomeFrame {
      actionLink("Configure").click()
      popupMenu("Plugins").clickSearchedItem()
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
    step("install plugin '$pluginName' from path '$pluginPath'") {
      welcomeFrame {
        actionLink("Configure").click()
        popupMenu("Plugins").clickSearchedItem()
        pluginDialog {
          showInstalledPlugins()
          if (isPluginInstalled(pluginName).not()) {
            showInstallPluginFromDiskDialog()
            installPluginFromDiskDialog {
              setPath(pluginPath)
              clickOk()
              ignoreComponentLookupException {
                dialog(title = "Warning", timeout = seconds05) {
                  message("Warning") {
                    button("OK").click()
                  }
                }
              }
            }
          }
          button("OK").click()
          ignoreComponentLookupException {
            dialog(title = "IDE and Plugin Updates", timeout = seconds05) {
              button("Postpone").click()
            }
          }
        }
      }
    }
  }

  private fun ensureButtonOkHasPressed(guiTestCase: GuiTestCase) {
    val dialogTitle = "Plugins"
    try {
      GuiTestUtilKt.waitUntilGone(robot = guiTestCase.robot(),
                                  timeout = seconds05,
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

  companion object {
    const val PLUGINS_INSTALLED = "PLUGINS_INSTALLED"
  }
}