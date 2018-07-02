// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures.extended

import com.intellij.testGuiFramework.cellReader.ExtendedJTreeCellReader
import com.intellij.testGuiFramework.cellReader.ProjectTreeCellReader
import com.intellij.testGuiFramework.cellReader.SettingsTreeCellReader
import com.intellij.testGuiFramework.driver.ExtendedJTreeDriver
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTreeFixture
import javax.swing.JTree
import javax.swing.tree.TreePath

class ExtendedTreeFixture(val tree: JTree, val treePath: TreePath, robot: Robot = GuiRobotHolder.robot) : JTreeFixture(robot, tree) {
  val myDriver by lazy {
    replaceDriverWith(ExtendedJTreeDriver(robot()))
    driver() as ExtendedJTreeDriver
  }
  val myCellReader by lazy {
    val resultReader = when (tree.javaClass.name) {
      "com.intellij.openapi.options.newEditor.SettingsTreeView\$MyTree" -> SettingsTreeCellReader()
      "com.intellij.ide.projectView.impl.ProjectViewPane\$1" -> ProjectTreeCellReader()
      else -> ExtendedJTreeCellReader()
    }
    replaceCellReader(resultReader)
    resultReader
  }


}