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
package com.intellij.testGuiFramework.impl

import org.fest.swing.core.Robot
import org.fest.swing.core.SmartWaitRobot
import org.junit.rules.ExternalResource

class RobotTestRule: ExternalResource() {

  private var myRobot: Robot? = null

  override fun before() {
    myRobot = SmartWaitRobot() // acquires ScreenLock
//    myRobot!!.settings().delayBetweenEvents(30)
  }

  override fun after() {
    myRobot!!.cleanUpWithoutDisposingWindows()  // releases ScreenLock
  }

  fun getRobot(): Robot {
    return myRobot ?: throw Exception("Robot hasn't been initialized yet!")
  }

}