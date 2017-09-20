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

import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JComboBoxFixture
import javax.swing.JButton
import javax.swing.JComboBox


class ComboBoxFixture(robot: Robot, comboBox: JComboBox<*>) : JComboBoxFixture(robot, comboBox) {

  fun expand(): ComboBoxFixture {
    val arrowButton = target().components.filter { it is JButton }.firstOrNull() ?: throw ComponentLookupException(
      "Unable to find bounded arrow button for a combobox")
    robot().click(arrowButton)
    return this
  }

  //We are waiting for a item to be shown in dropdown list. It is necessary for a async comboboxes
  fun selectItem(itemName: String, timeoutInSeconds: Int = 30): ComboBoxFixture {
    waitUntil("item '$itemName' will be appeared in dropdown list", timeoutInSeconds) {
      doSelectItem({ super.selectItem(itemName) })
    }
    return this
  }

  //We are waiting for a item to be shown in dropdown list. It is necessary for a async comboboxes
  fun selectItem(itemIndex: Int, timeoutInSeconds: Int = 30): ComboBoxFixture {
    waitUntil("item with index $itemIndex will be appeared in dropdown list", timeoutInSeconds) {
      doSelectItem({ super.selectItem(itemIndex) })
    }
    return this
  }

  override fun selectItem(index: Int): ComboBoxFixture {
    return selectItem(index, 30)
  }

  fun listItems(): List<String> {
    return (0 until target().itemCount).map { target().getItemAt(it) }.map { it.toString() }
  }

  private fun doSelectItem(selectItemFunction: () -> Unit): Boolean {
    return try {
      selectItemFunction()
      true
    }
    catch (e: Exception) {
      when (e) {
        is LocationUnavailableException -> false
        is IndexOutOfBoundsException -> false
        else -> throw e
      }
    }
  }
}