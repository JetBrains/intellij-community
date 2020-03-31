// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import org.fest.swing.core.Robot
import javax.swing.Box

class MainToolbarFixture(myRobot: Robot, private val myToolbar: ActionToolbarImpl, private val myIdeFrame: IdeFrameFixture) :
  ComponentFixture<MainToolbarFixture, ActionToolbarImpl>(MainToolbarFixture::class.java, myRobot, myToolbar) {

  fun show() {
    if (!isShowing()) myIdeFrame.invokeMainMenu("ViewToolBar")
  }

  fun hide() {
    if (isShowing()) myIdeFrame.invokeMainMenu("ViewToolBar")
  }

  fun isShowing(): Boolean = myToolbar.isShowing

  companion object {
    fun createMainToolbarFixture(robot: Robot, ideFrame: IdeFrameFixture): MainToolbarFixture {
      val actionToolbar = robot.finder()
        .find(ideFrame.target()) { component -> component is ActionToolbarImpl && isMainToolbar(component) } as ActionToolbarImpl
      return MainToolbarFixture(robot, actionToolbar, ideFrame)
    }

    fun isMainToolbar(actionToolbarImpl: ActionToolbarImpl): Boolean =
      (actionToolbarImpl.parent is Box)
    //actionToolbarImpl.parent.javaClass.name.contains(NavBarRootPaneExtension::class.java.simpleName)
  }

}