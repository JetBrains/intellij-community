// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import java.awt.Container
import java.awt.Point
import javax.swing.JCheckBox
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

  fun clickCheckbox(tree: CheckboxTree, path: TreePath) {
    val checkBox = getCheckboxComponent(tree, path) ?: throw ComponentLookupException("Unable to find checkBox for a ExtCheckboxTree with path $path")
    val pathBounds = tree.getPathBounds(path)
    val checkBoxCenterPoint = Point(pathBounds.x + checkBox.location.x + checkBox.width / 2,
                                    pathBounds.y + checkBox.location.y + checkBox.height / 2)
    tree.scrollToPath(path)
    tree.makeVisible(path)
    robot.click(tree, checkBoxCenterPoint)
    robot.waitForIdle()
  }


}
