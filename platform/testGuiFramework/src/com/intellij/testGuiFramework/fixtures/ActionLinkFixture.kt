// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testGuiFramework.impl.findComponent
import com.intellij.ui.components.labels.ActionLink
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.driver.ComponentDriver
import org.fest.swing.driver.JComponentDriver
import java.awt.Component
import java.awt.Container
import java.awt.Point

class ActionLinkFixture constructor(robot: Robot, target: ActionLink) : JComponentFixture<ActionLinkFixture, ActionLink>(
  ActionLinkFixture::class.java, robot, target) {

  init {
    replaceDriverWith(ActionLinkDriver(robot))
  }

  internal class ActionLinkDriver(robot: Robot) : JComponentDriver<ActionLink>(robot) {

    override fun click(c: ActionLink) {
      clickActionLinkText(c, MouseButton.LEFT_BUTTON, 1)
    }

    override fun click(c: ActionLink, button: MouseButton) {
      clickActionLinkText(c, button, 1)
    }

    override fun click(c: ActionLink, mouseClickInfo: MouseClickInfo) {
      clickActionLinkText(c, mouseClickInfo.button(), mouseClickInfo.times())
    }

    override fun doubleClick(c: ActionLink) {
      clickActionLinkText(c, MouseButton.LEFT_BUTTON, 2)
    }

    override fun rightClick(c: ActionLink) {
      clickActionLinkText(c, MouseButton.RIGHT_BUTTON, 2)
    }

    override fun click(c: ActionLink, button: MouseButton, times: Int) {
      clickActionLinkText(c, button, times)
    }

    override fun click(c: ActionLink, where: Point) {
      click(c, where, MouseButton.LEFT_BUTTON, 1)
    }

    private fun clickActionLinkText(c: Component, mouseButton: MouseButton, times: Int) {
      assert(c is ActionLink)
      val textRectangleCenter = (c as ActionLink).textRectangleCenter
      click(c, textRectangleCenter, mouseButton, times)
    }

    private fun click(c: Component, where: Point, mouseButton: MouseButton, times: Int) {
      ComponentDriver.checkInEdtEnabledAndShowing(c)
      this.robot.click(c, where, mouseButton, times)
    }

  }

  companion object {

    fun findByActionId(actionId: String, robot: Robot, container: Container?): ActionLinkFixture {
      val actionLink = robot.findComponent(container, ActionLink::class.java) {
        if (it.isVisible && it.isShowing)
          actionId == ActionManager.getInstance().getId(it.action)
        else
          false
      }
      return ActionLinkFixture(robot, actionLink)
    }

    fun actionLinkFixtureByName(actionName: String, robot: Robot, container: Container): ActionLinkFixture {
      val actionLink = robot.findComponent(container, ActionLink::class.java) {
        if (it.isVisible && it.isShowing) {
          it.text == actionName
        }
        else false
      }
      return ActionLinkFixture(robot, actionLink)
    }
  }

}
