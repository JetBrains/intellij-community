// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.impl

import org.fest.swing.core.Robot
import org.fest.swing.core.SmartWaitRobot

object GuiRobotHolder {
  @Volatile
  private var myRobot: Robot? = null
  val robot: Robot
    get() {
      if(myRobot == null)
        synchronized(this) {
          if(myRobot == null) initializeRobot()
        }
      return myRobot ?: throw IllegalStateException("Cannot initialize the robot")
    }

  private fun initializeRobot() {
    if (myRobot != null) releaseRobot()
    myRobot = SmartWaitRobot() // acquires ScreenLock
  }

  fun releaseRobot() {
    if(myRobot != null) {
      synchronized(this){
        if (myRobot != null){
          myRobot!!.cleanUpWithoutDisposingWindows()  // releases ScreenLock
          myRobot = null
        }
      }
    }
  }
}