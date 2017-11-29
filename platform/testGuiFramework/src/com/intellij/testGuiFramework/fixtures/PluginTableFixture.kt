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

import com.intellij.ide.plugins.PluginTable
import com.intellij.testGuiFramework.framework.GuiTestUtil
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.awt.Container
import java.awt.Point

/**
 * @author Sergey Karashevich
 */
class PluginTableFixture(robot: Robot, pluginTable: PluginTable) : ComponentFixture<PluginTableFixture, PluginTable>(PluginTableFixture::class.java, robot,
                                                                                                         pluginTable) {
  companion object {

    fun find(robot: Robot, container: Container, timeout: Timeout): PluginTableFixture {
      val matcher = object : GenericTypeMatcher<PluginTable>(PluginTable::class.java) {
        override fun isMatching(pluginTable: PluginTable) = true
      }

      Pause.pause(object : Condition("Finding for PluginTable component") {
        override fun test(): Boolean {
          val pluginTables = robot.finder().findAll(container, matcher)
          return !pluginTables.isEmpty()
        }
      }, timeout)

      val pluginTable = robot.finder().find(container, matcher)
      return PluginTableFixture(robot, pluginTable)
    }
  }

  fun selectPlugin(pluginName: String) {
    val pluginTable = this.target()
    Pause.pause(object: Condition("wait until row appeared") {
      override fun test() = (findRow(pluginName) != null)
    }, GuiTestUtil.SHORT_TIMEOUT)
    val cellRect = pluginTable.getCellRect(findRow(pluginName)!!, 0, false)
    robot().click(pluginTable, Point(cellRect.centerX.toInt(), cellRect.centerY.toInt()))
  }


  private fun findRow(pluginName: String): Int? {
    var rowNumber: Int? = null
    for(i in (0..target().rowCount - 1)) {
      target().getObjectAt(i).name == pluginName
      rowNumber = i
      break
    }
    return rowNumber
  }

}