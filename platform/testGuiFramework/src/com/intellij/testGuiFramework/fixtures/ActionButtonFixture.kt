// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testGuiFramework.impl.findComponent
import org.fest.swing.core.Robot
import java.awt.Container

class ActionButtonFixture(robot: Robot, target: ActionButton) : JComponentFixture<ActionButtonFixture, ActionButton>(
  ActionButtonFixture::class.java, robot, target) {

  companion object {

    fun actionIdMatcher(actionId: String): (ActionButton) -> Boolean =
      {
        val buttonActionId = ActionManager.getInstance().getId(it.action)
        it.isEnabled && it.isShowing && buttonActionId != null && buttonActionId == actionId
      }

    fun actionClassNameMatcher(actionClassName: String): (ActionButton) -> Boolean = {
      (it.isShowing
       && it.isEnabled
       && it.action != null
       && it.action.javaClass.simpleName == actionClassName)
    }

    fun textMatcher(text: String): (ActionButton) -> Boolean = {
      if (!it.isShowing || !it.isEnabled) false
      else text == it.action.templatePresentation.text
    }

    fun fixtureByActionId(container: Container?, robot: Robot, actionId: String): ActionButtonFixture
      = ActionButtonFixture(robot, robot.findComponent(container, ActionButton::class.java, actionIdMatcher(actionId)))

    fun fixtureByActionClassName(container: Container?, robot: Robot, actionClassName: String): ActionButtonFixture
      = ActionButtonFixture(robot, robot.findComponent(container, ActionButton::class.java, actionClassNameMatcher(actionClassName)))

    fun fixtureByText(container: Container?, robot: Robot, text: String): ActionButtonFixture
      = ActionButtonFixture(robot, robot.findComponent(container, ActionButton::class.java, textMatcher(text)))
  }
}
