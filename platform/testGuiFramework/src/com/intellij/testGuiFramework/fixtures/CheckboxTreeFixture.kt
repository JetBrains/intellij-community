// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.CheckboxTreeDriver
import com.intellij.testGuiFramework.fixtures.extended.ExtendedJTreePathFixture
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot
import javax.swing.tree.TreePath

class CheckboxTreeFixture(
  checkboxTree: CheckboxTree,
  path: TreePath,
  robot: Robot = GuiRobotHolder.robot,
  private val myDriver: CheckboxTreeDriver = CheckboxTreeDriver(robot)
) : ExtendedJTreePathFixture(checkboxTree, path, robot) {

  init {
    this.replaceDriverWith(myDriver)
  }

  fun clickCheckbox() = myDriver.clickCheckbox(target() as CheckboxTree, path)

  fun getCheckboxComponent() = myDriver.getCheckboxComponent(target() as CheckboxTree, path)

  fun setCheckboxValue(value: Boolean) {
    val checkbox = getCheckboxComponent()
    if (checkbox != null && checkbox.isSelected != value) {
      clickCheckbox()
      val actualValue = getCheckboxComponent()?.isSelected
      assert(actualValue == value) {
        "Checkbox at path $path: actual value is $actualValue, but expected is $value"
      }
    }
  }

  fun check() = setCheckboxValue(true)

  fun uncheck() = setCheckboxValue(false)

}