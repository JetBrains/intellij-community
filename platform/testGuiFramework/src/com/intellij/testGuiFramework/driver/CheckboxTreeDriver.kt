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
package com.intellij.testGuiFramework.driver

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import java.awt.Container
import java.awt.Point
import javax.swing.JCheckBox

class CheckboxTreeDriver(robot: Robot) : ExtendedJTreeDriver(robot) {

  init {
    replaceCellReader(ExtendedJTreeCellReader())
  }

  fun getCheckboxComponent(tree: CheckboxTree, pathStrings: List<String>): JCheckBox? {
    val treePath = matchingPathFor(tree, pathStrings)
    val rendererComponent = tree.cellRenderer.getTreeCellRendererComponent(tree, treePath.lastPathComponent, false, false, false,
                                                                           tree.getRowForPath(treePath),
                                                                           false)
    return GuiTestUtilKt.findAllWithBFS(rendererComponent as Container, JCheckBox::class.java).firstOrNull()
  }

  fun clickCheckbox(tree: CheckboxTree, pathStrings: List<String>) {
    val treePath = matchingPathFor(tree, pathStrings)
    val checkBox = getCheckboxComponent(tree, pathStrings) ?: throw ComponentLookupException("Unable to find checkBox for a CheckboxTree with path $pathStrings")
    val pathBounds = tree.getPathBounds(treePath)
    val checkBoxCenterPoint = Point(pathBounds.x + checkBox.location.x + checkBox.width / 2,
                                    pathBounds.y + checkBox.location.y + checkBox.height / 2)
    scrollToPath(tree, pathStrings)
    robot.click(tree, checkBoxCenterPoint)
  }


}
