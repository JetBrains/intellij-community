// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.intellij.uiTests.e2e.fixtures.idea
import com.intellij.uiTests.e2e.steps.CreateNewProjectSteps
import com.intellij.uiTests.robot.StepPrinter
import org.junit.After
import org.junit.BeforeClass
import java.awt.event.KeyEvent
import java.net.ConnectException
import java.time.Duration

internal abstract class UITest {
  companion object {
    @JvmStatic
    protected val robot = RemoteRobot("http://127.0.0.1:8580").apply {
      StepWorker.registerProcessor(StepPrinter())
    }

    @JvmStatic
    protected val newProjectSteps = CreateNewProjectSteps(robot)

    @JvmStatic
    @BeforeClass
    fun before() {
      step("Wait for ide started") {
        waitFor(Duration.ofSeconds(10), errorMessage = "Ide is not started") {
          try {
            val ideaVersion = getIdeaVersion()
            println(ideaVersion)
            true
          }
          catch (e: ConnectException) {
            false
          }
        }
      }
    }

    private fun getIdeaVersion(): String {
      return robot.callJs<String>("""
        importPackage(com.intellij.openapi.application)
        const info = ApplicationInfo.getInstance()
        info.getFullVersion() + ': ' + info.getBuild()
    """)
    }
  }

  @After
  fun closeProject() {
    robot.idea {
      if (robot.isMac()) {
        keyboard {
          hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_META, KeyEvent.VK_A)
          enterText("Close Project")
          enter()
        }
      } else {
        menuBar.select("File", "Close Project")
      }
    }
  }
}