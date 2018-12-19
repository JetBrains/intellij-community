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

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.testGuiFramework.util.step
import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.LocationUnavailableException
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.timing.Timeout
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComboBox


class ComboBoxFixture(robot: Robot, comboBox: JComboBox<*>) : JComboBoxFixture(robot, comboBox) {

  fun expand(): ComboBoxFixture {
    val arrowButton = target().components.firstOrNull { it is JButton } ?: throw ComponentLookupException(
      "Unable to find bounded arrow button for a combobox")
    robot().click(arrowButton)
    return this
  }

  fun expandAndClose(): ComboBoxFixture {
    expand()
    robot().pressAndReleaseKeys(KeyEvent.VK_ESCAPE)
    return this
  }

  //We are waiting for a item to be shown in dropdown list. It is necessary for a async comboboxes
  fun selectItem(itemName: String, timeout: Timeout = Timeouts.defaultTimeout): ComboBoxFixture {
    return step("select '$itemName' in combobox") {
      waitUntil("item '$itemName' will be appeared in dropdown list", timeout) {
        doSelectItem { super.selectItem(itemName) }
      }
      return@step this
    }
  }

  //We are waiting for a item to be shown in dropdown list. It is necessary for a async comboboxes
  fun selectItem(itemIndex: Int, timeout: Timeout = Timeouts.defaultTimeout): ComboBoxFixture {
    waitUntil("item with index $itemIndex will be appeared in dropdown list", timeout) {
      doSelectItem { super.selectItem(itemIndex) }
    }
    return this
  }

  override fun selectItem(index: Int): ComboBoxFixture {
    super.selectItem(index)
    return this
  }

  /**
   * Returns list of rendered values
   * Nulls are not allowed - a rendered value cannot be a null
   * */
  fun listItems(): List<String> {
    return (0 until target().itemCount).map { driver().value(target(), it).toString() }
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

  /**
   * JComboBox.getSelectedIndex assumes that an item in the model cannot be null
   *
   *This function copies body of [JComboBox.getSelectedIndex] but with null allowed
   *
   */
  val selectedIndex: Int
    get() {
      val noSelectedIndex = -1
      val model = (target() as? JComboBox)?.model
                  ?: throw IllegalStateException("ComboBoxFixture wraps not a JComboBox")
      val selectedItem = model.selectedItem
      return (0 until model.size)
               .firstOrNull { model.getElementAt(it) == selectedItem }
             ?: noSelectedIndex
    }

  /**
   * 1. selectedItem should return visible value of value from Cell Renderer
   * 2. to workaround problem with selected `null` value [selectedIndex] is used
   * */
  override fun selectedItem(): String? {
    return getRenderedValueAtIndex(selectedIndex)
  }

  /**
   * Returns rendered value got through Cell Renderer
   * might differ from value got from the component itself
   *
   * @return rendered value at the specified index
   * */
  private fun getRenderedValueAtIndex(index: Int) =
    driver().value(target(), index)
}