// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.InplaceButton
import org.fest.swing.core.ComponentMatcher
import org.fest.swing.core.Robot
import org.fest.swing.timing.Timeout
import java.awt.Container
import java.util.concurrent.TimeUnit
import javax.swing.Icon

class InplaceButtonFixture(selfType: Class<InplaceButtonFixture>,
                           robot: Robot,
                           target: InplaceButton) : JComponentFixture<InplaceButtonFixture, InplaceButton>(selfType, robot, target) {

  companion object {

    fun findInplaceButtonFixture(root: Container, robot: Robot, icon: Icon, timeoutInSeconds: Long): InplaceButtonFixture {
      if (timeoutInSeconds < 0L) throw Exception("Unable to wait less than 0 seconds")
      val inplaceButton = if (timeoutInSeconds == 0L) {
        findInplaceButton(root, robot, icon)
      }
      else {
        GuiTestUtil.waitUntilFound(robot, root, GuiTestUtilKt.typeMatcher(InplaceButton::class.java, { it.icon == icon }),
                                   Timeout.timeout((timeoutInSeconds), TimeUnit.SECONDS))
      }
      return InplaceButtonFixture(InplaceButtonFixture::class.java, robot, inplaceButton)
    }

    private fun findInplaceButton(root: Container, robot: Robot, icon: Icon): InplaceButton {
      return robot.finder().find(root, ComponentMatcher { it is InplaceButton && it.icon == icon }) as InplaceButton
    }

  }

}