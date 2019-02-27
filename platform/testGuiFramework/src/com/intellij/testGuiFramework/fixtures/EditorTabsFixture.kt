// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.findAllWithBFS
import com.intellij.ui.InplaceButton
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.tabs.JBEditorTabsBase
import com.intellij.ui.tabs.impl.TabLabel
import org.fest.swing.core.Robot
import org.fest.swing.timing.Timeout
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle


class EditorTabsFixture(val robot: Robot, val ideFrameFixture: IdeFrameFixture) {

  private fun findTab(tabName: String): TabLabel? {
    return robot.finder().findAll(
      ideFrameFixture.target()) {
      it is TabLabel
      && it.isShowing
      && it.isVisible
      && it.isEnabled
      && it.parent is JBEditorTabsBase
    }
      .filterIsInstance(TabLabel::class.java)
      .firstOrNull { findAllWithBFS(it as Container, SimpleColoredComponent::class.java).firstOrNull()?.getText() == tabName }
  }

  fun hasTab(tabName: String): Boolean {
    return findTab(tabName) != null
  }

  fun selectTab(tabName: String) {
    val tab = findTab(tabName) ?: throw Exception("Unable to select (cannot be found) editor tab with name \"$tabName\"")
    robot.click(tab)
  }

  fun closeTab(tabName: String) {
    val tab = findTab(tabName) ?: throw Exception("Unable to select (cannot be found) editor tab with name \"$tabName\"")
    val closeButton = findAllWithBFS(tab, InplaceButton::class.java ).firstOrNull() ?: throw Exception("Unable to find close button for the editor tab with name \"$tabName\"")
    robot.click(closeButton)
  }

  fun waitTab(tabName: String, timeoutInSeconds: Timeout = Timeouts.seconds30): EditorTabsFixture {
    GuiTestUtilKt.waitUntil("editor tab with name '$tabName' has appeared", timeoutInSeconds) { hasTab(tabName) }
    return this
  }

  private fun SimpleColoredComponent.getText(): String?
    = this.iterator().asSequence().joinToString()

}

private operator fun Point.plus(location: Point): Point {
  return Point(this.x + location.x, this.y + location.y)
}

private fun Rectangle.center(): Point {
  return Point(this.x + this.width / 2, this.y + this.height / 2)
}
