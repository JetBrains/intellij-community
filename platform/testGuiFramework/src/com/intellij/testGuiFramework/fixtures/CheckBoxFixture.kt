// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.CheckBoxDriver
import com.intellij.testGuiFramework.impl.findComponent
import org.fest.swing.core.Robot
import org.fest.swing.driver.AbstractButtonDriver
import java.awt.Container
import javax.swing.JCheckBox

/**
 * @author Sergey Karashevich
 */
class CheckBoxFixture(robot: Robot, target: JCheckBox) : org.fest.swing.fixture.JCheckBoxFixture(robot, target) {

  var isSelected: Boolean
    get() = target().isSelected
    set(value: Boolean) {
      target().isSelected = value
    }

  override fun createDriver(robot: Robot): AbstractButtonDriver = CheckBoxDriver(robot)

  companion object {

    fun findByText(text: String, root: Container?, robot: Robot): CheckBoxFixture {
      val jCheckBox = robot.findComponent(root, JCheckBox::class.java) {
        it.text != null && it.text.toLowerCase() == text.toLowerCase()
      }
      return CheckBoxFixture(robot, jCheckBox)
    }
  }
}
