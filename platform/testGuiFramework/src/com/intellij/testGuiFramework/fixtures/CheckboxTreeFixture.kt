// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.CheckboxTreeDriver
import com.intellij.testGuiFramework.driver.ExtendedJTreePathFinder
import com.intellij.testGuiFramework.driver.FinderPredicate
import com.intellij.testGuiFramework.fixtures.extended.ExtendedJTreePathFixture
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot

class CheckboxTreeFixture(
  checkboxTree: CheckboxTree,
  stringPath: List<String>,
  predicate: FinderPredicate = ExtendedJTreePathFinder.predicateEquality,
  robot: Robot = GuiRobotHolder.robot,
  private val myDriver: CheckboxTreeDriver = CheckboxTreeDriver(robot)
) : ExtendedJTreePathFixture(checkboxTree, stringPath, predicate, robot, myDriver) {

  init {
    this.replaceDriverWith(myDriver)
  }

  fun clickCheckbox() = myDriver.clickCheckbox(target() as CheckboxTree, path)

  fun getCheckboxComponent() = myDriver.getCheckboxComponent(target() as CheckboxTree, path)

  fun setCheckboxValue(value: Boolean) {
    val checkbox = getCheckboxComponent()
    if (checkbox != null && checkbox.isSelected != value) {
      clickCheckbox()
      GuiTestUtilKt.waitUntil("Wait until checkbox got value $value") {
        getCheckboxComponent()?.isSelected ?: false
      }
      val actualValue = getCheckboxComponent()?.isSelected
      assert(actualValue == value) {
        "Checkbox at path $path: actual value is $actualValue, but expected is $value"
      }
    }
  }

  fun check() = setCheckboxValue(true)

  fun uncheck() = setCheckboxValue(false)

}