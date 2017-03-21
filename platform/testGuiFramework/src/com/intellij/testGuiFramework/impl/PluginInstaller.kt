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
package com.intellij.testGuiFramework.impl

import com.intellij.testGuiFramework.framework.GuiTestUtil
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Test

/**
 * @author Sergey Karashevich
 */
class PluginInstaller: GuiTestCase() {

  private val GUI_TEST_ADD_PLUGINS = "GUI_TEST_ADD_PLUGINS"

  @Test
  fun testInstallPlugin() {
    val pluginName = GuiTestUtil.getSystemPropertyOrEnvironmentVariable(GUI_TEST_ADD_PLUGINS)
    if (pluginName == null) Assert.fail("System property and Environment variable for key \"$GUI_TEST_ADD_PLUGINS\" are null")

    welcomeFrame {
      actionLink("Configure").click()
      popupClick("Plugins")
      dialog("Plugins") {
        button("Install JetBrains plugin...").click()
        dialog("Browse JetBrains Plugins ") {
          textfield("").click()
          typeText(pluginName!!)
          pluginTable().selectPlugin(pluginName)
          screenshot(this.target(), "BrowseJetBrainsPlugins")
          button("Install").click()
          button("Restart IntelliJ IDEA").click()
          dialog("This should not be shown") {
            button("Postpone").click()
          }
        }
      }
      Pause.pause(2000) //wait when all modal dialogs are gone
    }
  }

}