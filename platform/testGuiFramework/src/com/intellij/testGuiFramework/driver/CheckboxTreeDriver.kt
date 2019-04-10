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
import javax.swing.JTree
import javax.swing.tree.TreePath

class CheckboxTreeDriver(robot: Robot) : ExtendedJTreeDriver(robot) {

  init {
    replaceCellReader(ExtendedJTreeCellReader())
  }

  fun getCheckboxComponent(tree: CheckboxTree, path: TreePath): JCheckBox? {
    val rendererComponent = tree.cellRenderer.getTreeCellRendererComponent(tree, path.lastPathComponent, false, false, false,
                                                                           tree.getRowForPath(path),
                                                                           false)
    return GuiTestUtilKt.findAllWithBFS(rendererComponent as Container, JCheckBox::class.java).firstOrNull()
  }

  private fun CheckboxTree.clickRow(path: TreePath, calculatePoint: (Rectangle, Rectangle) -> Point){
    val checkbox = getCheckboxComponent(this, path) ?: throw ComponentLookupException("Unable to find checkBox for a ExtCheckboxTree with path ${path.path.joinToString()}")
    val point = GuiTestUtilKt.computeOnEdt {
      val pathBounds = GuiTestUtilKt.computeOnEdt { this.getPathBounds(path) }!!
       calculatePoint(checkbox.bounds, pathBounds)
    }
    this.scrollToPath(path)
    this.makeVisible(path)
    robot.click(this, point!!)
    robot.waitForIdle()

  }

  fun clickCheckbox(tree: CheckboxTree, path: TreePath) {
    tree.clickRow(path){
      checkboxBounds: Rectangle, pathBounds:Rectangle ->
      Point(pathBounds.x + checkboxBounds.x + checkboxBounds.width / 2,
            pathBounds.y + checkboxBounds.y + checkboxBounds.height / 2)
    }
  }

  override fun getLabelXCoord(jTree: JTree, path: TreePath): Int {
    val checkBox = getCheckboxComponent(jTree as CheckboxTree, path) ?: throw ComponentLookupException("Unable to find checkBox for a ExtCheckboxTree with path $path")
    val pathBounds = GuiTestUtilKt.computeOnEdt { jTree.getPathBounds(path) }!!

    return pathBounds.x + checkBox.bounds.width + 2
  }
}
