// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.util.ui.AsyncProcessIcon
import org.fest.swing.core.Robot

class AsyncProcessIconFixture(robot: Robot,
                              target: AsyncProcessIcon)
  : JComponentFixture<AsyncProcessIconFixture, AsyncProcessIcon>(AsyncProcessIconFixture::class.java, robot, target) {

  fun waitUntilStop(timeoutInSeconds: Int) {
    GuiTestUtilKt.waitUntil("async process icon will stop", timeoutInSeconds) { !target().isRunning }
  }

}