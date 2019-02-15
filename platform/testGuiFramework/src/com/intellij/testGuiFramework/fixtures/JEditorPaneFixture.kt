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
  private fun String.searchInString(search: String): IntRange{
    val ind = this.indexOf(search)
    return if(ind >= 0)
      (ind + 1)..(ind + search.length)
    else -1..-1
  }

  fun clickLabelAtText(text: String) {
    step("click at the text '$text' at label at JEditorPane") {
      val length = GuiTestUtilKt.computeOnEdt { target().document.length - 1 }!!
      val fullText = GuiTestUtilKt.computeOnEdt { target().document.getText(1, length) }!!
      val searchedRange = fullText.searchInString(text)
      val leftX = GuiTestUtilKt.computeOnEdt { target().modelToView2D(searchedRange.first).x }!!.toInt()
      val rightX = GuiTestUtilKt.computeOnEdt { target().modelToView2D(searchedRange.last).x }!!.toInt()
      val height = GuiTestUtilKt.computeOnEdt { target().height }!! / 2
      val clickPoint = Point((leftX + rightX) / 2, height)
      driver().click(target(), clickPoint)
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
