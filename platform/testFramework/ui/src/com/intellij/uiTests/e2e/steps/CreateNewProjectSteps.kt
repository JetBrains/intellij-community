// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e.steps

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.intellij.uiTests.e2e.fixtures.dialog
import com.intellij.uiTests.e2e.fixtures.idea
import com.intellij.uiTests.e2e.fixtures.welcomeFrame
import com.intellij.uiTests.robot.optional
import java.time.Duration

class CreateNewProjectSteps(private val robot: RemoteRobot) {
  fun createCommandLineProject() = step("Create CommandLine Project") {
    robot.welcomeFrame {
      createNewProjectLink.click()
      dialog("New Project") {
        findText("Java").click()
        checkBox("Add sample code").select()
        button("Create").click()
      }
    }
    robot.idea {
      waitFor(Duration.ofMinutes(5)) { isDumbMode().not() }
    }
    tryCloseTipOfTheDay()
  }

  private fun tryCloseTipOfTheDay(): Unit = step("Try to close 'Tip of The Day'") {
    robot.idea {
      optional {
        dialog("Tip of the Day", Duration.ofSeconds(5)) {
          button("Close").click()
        }
      }
    }
  }
}