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
package com.intellij.testGuiFramework.fixtures

import com.intellij.ide.ui.UISettings
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import javax.swing.JPanel

class NavigationBarFixture(robot: Robot, panel: JPanel, private val myIdeFrame: IdeFrameFixture) :
  ComponentFixture<NavigationBarFixture, JPanel>(NavigationBarFixture::class.java, robot, panel), ContainerFixture<JPanel> {

  private val VIEW_NAV_BAR_ACTION = "ViewNavigationBar"

  fun show() {
    if (!isShowing()) myIdeFrame.invokeMainMenu(VIEW_NAV_BAR_ACTION)
  }

  fun hide() {
    if (isShowing()) myIdeFrame.invokeMainMenu(VIEW_NAV_BAR_ACTION)
  }

  fun isShowing(): Boolean = UISettings.instance.showNavigationBar

  companion object {
    fun createNavigationBarFixture(robot: Robot, ideFrame: IdeFrameFixture): NavigationBarFixture {
      val navBarPanel = robot.finder()
        .find(ideFrame.target()) { component -> component is JPanel && isNavBar(component) } as JPanel
      return NavigationBarFixture(robot, navBarPanel, ideFrame)
    }

    fun isNavBar(navBarPanel: JPanel): Boolean = navBarPanel.name == "navbar"
  }

}