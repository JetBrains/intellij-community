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
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.findAllWithBFS
import com.intellij.ui.InplaceButton
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.TabLabel
import org.fest.swing.core.Robot
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
      && it.parent is JBEditorTabs
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

  fun waitTab(tabName: String, timeoutInSeconds: Int = 30): EditorTabsFixture {
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
