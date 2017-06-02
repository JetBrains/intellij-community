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
package com.intellij.testGuiFramework.test

import com.intellij.testGuiFramework.fixtures.EditorFixture
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.media.MediaController
import com.intellij.testGuiFramework.media.MediaController.inTime
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLayeredPane
import org.fest.swing.timing.Pause.pause
import java.util.concurrent.TimeUnit

class ParameterHintsDemo : GuiTestCase() {

  fun demo() {

    importProject("parameter-hints")

    ideFrame {
      MediaController.withMedia("/param_hints.mp3") {
        playByText("Something you’ll") {
          toolwindow(id = "Project") {
            projectView {
              waitForBackgroundTasksToFinish()
              path("parameter-hints").click()
              waitForBackgroundTasksToFinish()
              path("parameter-hints", "src", "ParameterHints").doubleClick()
            }
          }
        }

        editor {
          playByText("You’ll see when") {
            moveTo(254)
            shortcut("shift alt left")
            pause(1, TimeUnit.SECONDS)
            typeText("sente")
            lookup()
            shortcut("tab")
            typeText(".spli")
            lookup()
            shortcut("tab")
            pause(1, TimeUnit.SECONDS)
            typeText("\"")
            typeText("\\\\s")
          }

          play(from = inTime("00:14"), to = inTime("00:23")) {
            moveTo(316)
            shortcut("enter")
            typeText("new Ser")
            lookup()
            shortcut("tab")
            typeText("\"")
            typeText("/users/")
            shortcut("right")
            typeText(", ")
            typeText("8080")
            shortcut("shift meta enter")
          }

          play(from = inTime("00:23"), to = inTime("00:40")) {
            moveTo(435)
            shortcut("shift alt left")
            typeText("find")
            lookup()
            shortcut("tab")
            typeText("name")
            lookup()
            shortcut("tab")
            typeText(", nu")
            lookup()
            shortcut("tab")
            typeText(", nu")
            lookup()
            shortcut("tab")
            typeText(", 0, true")
          }
          play(from = inTime("00:40"), to = inTime("00:49")) {
            moveTo(465)
            shortcut("alt enter")
            pause(1000)
            shortcut("enter")
          }
          play(from = inTime("00:49"), to = inTime("00:58")) {
            moveTo(511)
            shortcut("enter")
            typeText("InStr")
            lookup()
            shortcut("enter")
            typeText(".")
            typeText("ran")
            lookup()
            shortcut("tab")
            typeText("10, 100")
            shortcut("shift meta enter")
            shortcut("enter")
          }
          play(from = inTime("00:58"), to = inTime("01:01")) {
            typeText("InStr")
            lookup()
            shortcut("enter")
            typeText(".")
            typeText("of")
            lookup()
            shortcut("tab")
            typeText("1")
            shortcut("shift meta enter")
          }
          play(from = inTime("01:01"), to = inTime("01:06")) {
            moveTo(618)
            shortcut("tab")
            shortcut("tab")
            typeText("Serv")
            lookup()
            shortcut("tab")
            typeText(".")
            lookup()
            shortcut("tab")
            typeText("\"user")
            //change signature
          }

          moveTo(651)
        }
        play(from = inTime("01:07"), to = inTime("01:12")) {
          invokeMainMenu("ChangeSignature")
          dialog("Change Signature") {
            pause(4000)
            button("Cancel").click()
          }
        }

        play(from = inTime("01:12"), to = inTime("01:18")) {
          editor {
            moveTo(654)
            //invoke an action "ShowIntentionActions" via keystroke string
            shortcut("alt enter")
            typeText("⎋")
          }
        }
        play(from = inTime("01:18"), to = inTime("01:39")) {
          editor {
            rightClick(654)
            popup("Add Method 'generateServiceName' to Blacklist...")
            dialog("Configure Parameter Name Hints") {
              pause(12000)
              findAndClickEditor()
              shortcut("meta end")
              shortcut("shift up")
              shortcut("shift meta left")
              pause(1000)
              shortcut("back_space")
              pause(1000)
              button("OK").click()
            }
          }
        }
        play(from = inTime("01:39"), to = inTime("01:42")) { }
        editor {
          play(from = inTime("01:42"), to = inTime("01:49")) {
            rightClick(654)
            pause(1000)
            popup("Disable Hints")
          }
          play(from = inTime("01:49"), to = inTime("01:54")) {
            rightClick(462)
            pause(1000)
            popup("Enable Parameter Name Hints")
          }
        }
        play(from = inTime("01:54"), to = inTime("02:06")) {
          shortcut("meta comma")
          dialog("Preferences") {
            pause(3000)
            jTree("Editor", "General", "Appearance").clickPath("Editor", "General", "Appearance")
            checkbox("Show parameter name hints").click()
            pause(1000)
            checkbox("Show parameter name hints").click()
            pause(4000)
            button("Configure...").click()
            dialog("Configure Parameter Name Hints") {
              pause(1000)
              button("Cancel").click()
            }
            button("Cancel").click()
          }
        }
      }
    }
  }



  fun EditorFixture.lookup() {
    waitUntilFound(null, JBLayeredPane::class.java, 5) { jbLayeredPane ->
      jbLayeredPane.javaClass.name.contains("com.intellij.codeInsight.lookup.impl.LookupUi")
    }
  }

  fun JDialogFixture.findAndClickEditor() {
    val editorTextField = waitUntilFound(this.target(), EditorTextField::class.java, 10) {
      editorTextField ->
      editorTextField.document.text.contains("ParameterHints")
    }
    robot().click(editorTextField)
  }
}

