// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JCheckBox
import javax.swing.tree.TreePath

class CheckboxTreeDriver(robot: Robot) : ExtendedJTreeDriver(robot) {

  init {
    replaceCellReader(ExtendedJTreeCellReader())
  }

  fun getCheckboxComponent(tree: CheckboxTree, path: TreePath): JCheckBox? {
    robot.waitForIdle()
    val rendererComponent = tree.cellRenderer.getTreeCellRendererComponent(tree, path.lastPathComponent, false, false, false,
                                                                           tree.getRowForPath(path),
                                                                           false)
    return GuiTestUtilKt.findAllWithBFS(rendererComponent as Container, JCheckBox::class.java).firstOrNull()
  }

  private fun CheckboxTree.clickRow(path: TreePath, calculatePoint: (Rectangle, Rectangle) -> Point){
    val checkBox = getCheckboxComponent(this, path) ?: throw ComponentLookupException("Unable to find checkBox for a ExtCheckboxTree with path $path")
    val pathBounds = this.getPathBounds(path)
    val point = calculatePoint(checkBox.bounds, pathBounds)
    this.scrollToPath(path)
    this.makeVisible(path)
    robot.click(this, point)
    robot.waitForIdle()

  }

  fun clickCheckbox(tree: CheckboxTree, path: TreePath) {
    tree.clickRow(path){
      checkboxBounds: Rectangle, pathBounds:Rectangle ->
      Point(pathBounds.x + checkboxBounds.x + checkboxBounds.width / 2,
            pathBounds.y + checkboxBounds.y + checkboxBounds.height / 2)
    }
  }

  /**
   * Clicks the label specified by [path]
   * out of checkbox area to keep the checkbox value unchanged
   * */
  fun clickLabel(tree: CheckboxTree, path: TreePath){
    tree.clickRow(path){
      checkboxBounds: Rectangle, pathBounds:Rectangle ->
      Point(pathBounds.x + checkboxBounds.width + 2,
            pathBounds.y + checkboxBounds.y + checkboxBounds.height / 2)
    }
  }

}
