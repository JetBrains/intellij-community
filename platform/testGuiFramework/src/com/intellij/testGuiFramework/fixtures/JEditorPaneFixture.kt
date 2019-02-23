// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.waitUntilFound
import com.intellij.testGuiFramework.util.step
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Timeout
import java.awt.Container
import java.awt.Point
import javax.swing.JEditorPane

class JEditorPaneFixture(robot: Robot, pane: JEditorPane) : JTextComponentFixture(robot, pane) {
  fun clickLabel() {
    step("click at label at JEditorPane") {
      val shift = GuiTestUtilKt.computeOnEdt { target().height }!! / 2
      driver().click(target(), Point(shift, shift))
    }
  }
}

fun <C : Container> ContainerFixture<C>.jEditorPaneFixture(containedText: String,
                                                           timeout: Timeout = Timeouts.seconds10): JEditorPaneFixture {
  return step("search JEditorPane with text '$containedText'") {
    val jTextField = waitUntilFound(target(), JEditorPane::class.java, timeout) { pane ->
      pane.isShowing && pane.text.contains(containedText)
    }
    return@step JEditorPaneFixture(robot(), jTextField)
  }
}
