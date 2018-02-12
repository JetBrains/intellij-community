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
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JButtonFixture
import javax.swing.JButton

class ExtendedButtonFixture(robot: Robot, button: JButton) : JButtonFixture(robot, button) {

  fun waitEnabled(timeoutInSeconds: Int = 30): ExtendedButtonFixture {
    waitUntil("Waiting $timeoutInSeconds sec until button with text ${target().text} will be enabled",
              timeoutInSeconds = timeoutInSeconds) { isEnabled }
    return this
  }

  fun clickWhenEnabled(timeoutInSeconds: Int = 30) {
    waitEnabled(timeoutInSeconds)
    super.click()
  }

}