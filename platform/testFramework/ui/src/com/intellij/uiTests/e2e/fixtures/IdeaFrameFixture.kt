// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.e2e.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun RemoteRobot.idea(function: IdeaFrameFixture.() -> Unit) {
  find<IdeaFrameFixture>(timeout = Duration.ofSeconds(10)).apply(function)
}

@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrameFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

  val projectViewTree
    get() = find<ContainerFixture>(byXpath("ProjectViewTree", "//div[@class='ProjectViewTree']"))

  val projectName
    get() = step("Get project name") { return@step callJs<String>("component.getProject().getName()") }

  val menuBar: JMenuBarFixture
    get() = step("Menu...") {
      return@step remoteRobot.find(JMenuBarFixture::class.java, JMenuBarFixture.byType())
    }

  @JvmOverloads
  fun dumbAware(timeout: Duration = Duration.ofMinutes(5), function: () -> Unit) {
    step("Wait for smart mode") {
      waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
        runCatching { isDumbMode().not() }.getOrDefault(false)
      }
      function()
      step("..wait for smart mode again") {
        waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
          isDumbMode().not()
        }
      }
    }
  }

  fun isDumbMode(): Boolean {
    return callJs("""
            const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                project ? com.intellij.openapi.project.DumbService.isDumb(project) : true
            } else { 
                true 
            }
        """, true)
  }
}