// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.impl.actionLink
import com.intellij.testGuiFramework.impl.popupMenu
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Frame

class WelcomeFrameFixture private constructor(robot: Robot,
                                              target: FlatWelcomeFrame) : ComponentFixture<WelcomeFrameFixture, FlatWelcomeFrame>(
  WelcomeFrameFixture::class.java, robot, target), ContainerFixture<FlatWelcomeFrame> {

  fun createNewProject(): WelcomeFrameFixture {
    findActionLinkByActionId("WelcomeScreen.CreateNewProject").click()
    return this
  }

  fun importProject(): WelcomeFrameFixture {
    findActionLinkByActionId("WelcomeScreen.ImportProject").click()
    return this
  }

  fun checkoutFrom(): WelcomeFrameFixture {
    findActionLinkByActionId("WelcomeScreen.GetFromVcs").click()
    return this
  }

  private fun findActionLinkByActionId(actionId: String): ActionLinkFixture {
    return ActionLinkFixture.findByActionId(actionId, robot(), target())
  }

  fun findMessageDialog(title: String): MessagesFixture<*> {
    return MessagesFixture.findByTitle(robot(), target(), title)
  }

  fun openPluginsDialog()/*: JDialogFixture*/{
    actionLink("Configure").click()
    popupMenu("Plugins").clickSearchedItem()
    // TODO: make return JDialogFixture object of Plugins dialog
    // need to rework PluginsDialogModel to get rid of GuiTestCase object
  }

  companion object {
    fun find(robot: Robot, timeout: Timeout = Timeouts.minutes05): WelcomeFrameFixture {
      Pause.pause(object : Condition("Welcome Frame to show up") {
        override fun test(): Boolean {
          for (frame in Frame.getFrames()) {
            if (frame is FlatWelcomeFrame && frame.isShowing()) {
              return true
            }
          }
          return false
        }
      }, timeout)

      for (frame in Frame.getFrames()) {
        if (frame is FlatWelcomeFrame && frame.isShowing()) {
          return WelcomeFrameFixture(robot, frame)
        }
      }
      throw ComponentLookupException("Unable to find 'Welcome' window")
    }

    fun findSimple(): WelcomeFrameFixture = find(GuiRobotHolder.robot)
  }
}
