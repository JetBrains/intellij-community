// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e

import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.intellij.uiTests.e2e.fixtures.idea
import org.junit.Test
import java.time.Duration
import java.time.Duration.ofSeconds

internal class CreateNewProjectTest : UITest() {

  @Test
  fun createNewProject() {
    newProjectSteps.createCommandLineProject()
    robot.idea {
      step("Launch application") {
        textEditor().apply {
          waitFor(ofSeconds(20)) { button(byXpath("//div[@class='TrafficLightButton']")).hasText("Analyzing...").not() }
          menuBar.select("Build", "Build Project")
          waitFor { gutter.getIcons().isNotEmpty() }
          gutter.getIcons().first { it.description.contains("run.svg") }.click()
        }
        step("Run from gutter") {
          find<CommonContainerFixture>(byXpath("//div[@class='HeavyWeightWindow']"), ofSeconds(4))
            .button(byXpath("//div[@disabledicon='execute.svg']"))
            .click()
        }
      }
    }
    val consoleLocator = byXpath("ConsoleViewImpl", "//div[@class='ConsoleViewImpl']")
    step("Wait for Console appears") {
      waitFor(Duration.ofMinutes(1)) { robot.findAll<ContainerFixture>(consoleLocator).isNotEmpty() }
    }
    step("Check the message") {
      waitFor(Duration.ofMinutes(1)) { robot.find<ContainerFixture>(consoleLocator).hasText("Hello world!") }
    }
  }
}