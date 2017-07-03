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
package com.intellij.testGuiFramework.tests.samples

import com.intellij.ide.ui.UISettings
import com.intellij.testGuiFramework.impl.GuiTestCase
import org.junit.Ignore
import org.junit.Test

class AddActionToolbarInKotlinTestTest : GuiTestCase() {
  @Test @Ignore
  @Throws(Exception::class)
  fun testAddActionToolbar() {

    simpleProject {

      if (!UISettings.instance.showMainToolbar) invokeMenuPath("View", "Toolbar")
      shortcut(keyStroke = "meta comma")

      dialog("Preferences") {
        jTree("Appearance & Behavior").clickPath("Appearance & Behavior/Menus and Toolbars")
        jTree("Main Toolbar").clickPath("Main Toolbar/Help")
        button("Add After...").click()

        dialog("Choose Actions To Add") {
          jTree().clickPath("All Actions/Main menu/File/Print...")
          button("OK").click()
        }
        button("OK").click()
      }

      projectView {
        selectProjectPane().expandByPath(project.name, "src", "Main.java").click()
      }
      actionButton("Print").waitUntilEnabledAndShowing().click()

      dialog("Print") {
        button("Cancel").click()
      }
    }

  }
}
