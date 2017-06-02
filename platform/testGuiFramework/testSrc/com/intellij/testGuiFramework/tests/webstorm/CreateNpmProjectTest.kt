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
package com.intellij.testGuiFramework.tests.webstorm

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.IdeType
import org.junit.Test

@RunWithIde(IdeType.WEBSTORM)
class CreateNpmProjectTest : GuiTestCase() {

  @Test
  fun testCreateNpmProjectTest() {
    ideFrame {
      toolwindow(id = "Project") {
        projectView {
          path(project.name).rightClick()
        }
      }
      popup("New", "File")
      typeText("package.json")
      button("OK").click()

      val jsonContent = """{
  "scripts": {
    "start": "react-scripts start",
    "build": "react-scripts build",
    "test": "react-scripts test --env=jsdom",
    "eject": "react-scripts eject"
   """
      editor {
        waitForBackgroundTasksToFinish()
        moveTo(1)
        typeText(jsonContent)
      }
      toolwindow(id = "Projec") {
        projectView {
          path(project.name, "package.json").rightClick()
          popup("Show npm Scripts")
        }
      }
//      toolwindow(id = "npm") {
//        content(tabName = "") {
//          jTree("null").clickPath("null")
//          jTree("null/null").clickPath("null/null")
//        }
//      }
    }
  }

}
