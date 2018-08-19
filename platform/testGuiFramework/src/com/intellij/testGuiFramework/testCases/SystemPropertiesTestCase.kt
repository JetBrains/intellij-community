// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.testCases

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.RunWithSystemPropertiesContainer
import com.intellij.testGuiFramework.remote.transport.TransportMessage

/**
 * Used to run IDE with a custom JVM options defined in code.
 */
open class SystemPropertiesTestCase : GuiTestCase() {

  /**
   * @systemProperties - array of key-value pairs of custom JVM options written without -D, for example:
   * {@code arrayOf(Pair("idea.debug.mode", "true"))
   *
   * @setUpBlock block which is executed before test running. Using of ideFrame in this block is deprecated. Used for files manipulating or
   * setting up environment.
   */
  fun restartIdeWithSystemProperties(systemProperties: Array<Pair<String, String>>,
                                     setUpBlock: (Array<Pair<String, String>>) -> Unit = { }) {
    if (guiTestRule.getTestName() == GuiTestOptions.resumeTestName && GuiTestOptions.resumeInfo == SYSTEM_PROPERTIES) return
    setUpBlock(systemProperties)
    GuiTestThread.client?.send(createTransportMessage(systemProperties)) ?: throw Exception(
      "Unable to get the client instance to send message.")
    GuiTestUtilKt.waitUntil("IDE will be closed", timeout = Timeouts.minutes02) { false }
  }

  private fun createTransportMessage(systemProperties: Array<Pair<String, String>>) =
    TransportMessage(MessageType.RESTART_IDE_AND_RESUME, RunWithSystemPropertiesContainer(systemProperties))

  companion object {
    const val SYSTEM_PROPERTIES = "SYSTEM_PROPERTIES"
  }

}