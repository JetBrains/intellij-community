// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import org.fest.swing.core.Robot

object GuiRobot {
  private var _robot: Robot? = null
  val robot: Robot
    get() = _robot ?: throw IllegalStateException("Robot must be initialized before using")

  fun initializeRobot(robot: Robot) {
    if (_robot != null) releaseRobot() //throw IllegalStateException("Robot already has been initialized")
    _robot = robot
  }

  fun releaseRobot() {
    _robot = null
  }
}