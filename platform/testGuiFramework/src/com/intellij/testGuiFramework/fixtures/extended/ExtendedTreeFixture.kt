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
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.cellReader.ProjectTreeCellReader
import com.intellij.testGuiFramework.cellReader.SettingsTreeCellReader
import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import org.fest.swing.cell.JTreeCellReader
import org.fest.swing.core.MouseButton
import org.fest.swing.core.MouseClickInfo
import org.fest.swing.core.Robot
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JTreeFixture
import java.util.*
import javax.swing.JTree
import javax.swing.tree.TreePath

open class ExtendedTreeFixture(val robot: Robot, val tree: JTree) : JTreeFixture(robot, tree) {

  val myDriver = ExtendedJTreeDriver(robot)
  val myCellReader: JTreeCellReader

  init {
    replaceDriverWith(myDriver)
    myCellReader = defineCellReader()
  }

  fun hasXPath(vararg pathStrings: String): Boolean{
    return hasPath(pathStrings.toList())
  }

  fun hasPath(pathStrings: List<String>): Boolean {
    try {
      myDriver.checkPathExists(tree, pathStrings)
      return true
    }
    catch (lue: LocationUnavailableException) {
      return false
    }
  }

  fun clickPath(pathStrings: List<String>, mouseClickInfo: MouseClickInfo)
    = myDriver.clickPath(tree, pathStrings, mouseClickInfo)

  fun clickPath(vararg pathStrings: String, mouseClickInfo: MouseClickInfo)
    = myDriver.clickPath(tree, pathStrings.toList(), mouseClickInfo)

  fun clickPath(pathStrings: List<String>, button: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1)
    = myDriver.clickPath(tree, pathStrings, button, times)

  fun clickXPath(vararg xPathStrings: String)
    = myDriver.clickXPath(tree, xPathStrings.toList())

  fun clickXPath(xPathStrings: List<String>) {
    myDriver.clickXPath(tree, xPathStrings, MouseButton.LEFT_BUTTON, 1)
  }

  fun clickPath(vararg pathStrings: String, button: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1)
    = myDriver.clickPath(tree, pathStrings.toList(), button, times)

  fun checkPathExists(pathStrings: List<String>) = myDriver.checkPathExists(tree, pathStrings)

  fun checkPathExists(vararg pathStrings: String) = myDriver.checkPathExists(tree, pathStrings.toList())

  fun nodeValue(pathStrings: List<String>): String? = myDriver.nodeValue(tree, pathStrings)

  fun nodeValue(vararg pathStrings: String): String? = myDriver.nodeValue(tree, pathStrings.toList())

  fun doubleClickPath(pathStrings: List<String>) = myDriver.doubleClickPath(tree, pathStrings)

  fun doubleClickPath(vararg pathStrings: String) = myDriver.doubleClickPath(tree, pathStrings.toList())

  fun doubleClickXPath(vararg pathStrings: String) = myDriver.doubleClickXPath(tree, pathStrings.toList())

  fun rightClickPath(pathStrings: List<String>) = myDriver.rightClickPath(tree, pathStrings)

  fun rightClickPath(vararg pathStrings: String) = myDriver.rightClickPath(tree, pathStrings.toList())

  fun rightClickXPath(vararg pathStrings: String) = myDriver.rightClickXPath(tree, pathStrings.toList())

  fun expandPath(pathStrings: List<String>) = myDriver.expandPath(tree, pathStrings)

  fun expandPath(vararg pathStrings: String) = myDriver.expandPath(tree, pathStrings.toList())

  fun expandXPath(vararg pathStrings: String) = myDriver.expandXPath(tree, pathStrings.toList())

  fun collapsePath(pathStrings: List<String>) = myDriver.collapsePath(tree, pathStrings)

  fun collapsePath(vararg pathStrings: String) = myDriver.collapsePath(tree, pathStrings.toList())

  fun collapseXPath(vararg pathStrings: String) = myDriver.collapseXPath(tree, pathStrings.toList())

  fun selectPath(pathStrings: List<String>) = myDriver.selectPath(tree, pathStrings)

  fun selectPath(vararg pathStrings: String) = myDriver.selectPath(tree, pathStrings.toList())

  fun selectXPath(vararg pathStrings: String) = myDriver.selectXPath(tree, pathStrings.toList())

  fun scrollToPath(pathStrings: List<String>) = myDriver.scrollToPath(tree, pathStrings)

  fun scrollToPath(vararg pathStrings: String) = myDriver.scrollToPath(tree, pathStrings.toList())

  fun scrollToXPath(vararg pathStrings: String) = myDriver.scrollToXPath(tree, pathStrings.toList())

  fun showPopupMenu(pathStrings: List<String>) = myDriver.showPopupMenu(tree, pathStrings)

  fun showPopupMenu(vararg pathStrings: String) = myDriver.showPopupMenu(tree, pathStrings.toList())

  fun drag(pathStrings: List<String>) = myDriver.drag(tree, pathStrings)

  fun drag(vararg pathStrings: String) = myDriver.drag(tree, pathStrings.toList())

  fun drop(pathStrings: List<String>) = myDriver.drop(tree, pathStrings)

  fun drop(vararg pathStrings: String) = myDriver.drop(tree, pathStrings.toList())

  fun getPath(treePath: TreePath): List<String> {
    var path = treePath
    val result = ArrayList<String>()
    while (path.pathCount != 1 || (tree.isRootVisible && path.pathCount == 1)) {
      val valueAt = myCellReader.valueAt(tree, path.lastPathComponent) ?: "null"
      result.add(0, valueAt)
      if (path.pathCount == 1) break
      else path = path.parentPath
    }
    return result
  }

  private fun defineCellReader() : JTreeCellReader {
    var resultReader: JTreeCellReader
    when (tree.javaClass.name) {
      "com.intellij.openapi.options.newEditor.SettingsTreeView\$MyTree" -> resultReader = SettingsTreeCellReader()
      "com.intellij.ide.projectView.impl.ProjectViewPane\$1" -> resultReader = ProjectTreeCellReader()
      else -> resultReader = ExtendedJTreeCellReader()
    }
    replaceCellReader(resultReader)
    return resultReader
  }

}