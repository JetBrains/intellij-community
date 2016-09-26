/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.framework

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.ui.components.JBList
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import java.awt.Container

/**
    We presume that IDEA is already started, shows "Welcome dialog" and we could create a new project

 !!UNFINISHED!!
**/


fun GuiTestCase.createNewProject(): com.intellij.testGuiFramework.fixtures.WelcomeFrameFixture {
  return findWelcomeFrame().createNewProject()
}

fun pause(time: Long, root: Robot) {
  Pause.pause(object : Condition("Wait for user actions") {
    override fun test(): Boolean {
      return false
    }
  }, time)

}

fun clickListItem(itemName: String, robot: Robot, parentContainer: Container) {
  val listFixture = JListFixture(robot, robot.finder().findByType(parentContainer, JBList::class.java, true))
  listFixture.clickItem(itemName)
}