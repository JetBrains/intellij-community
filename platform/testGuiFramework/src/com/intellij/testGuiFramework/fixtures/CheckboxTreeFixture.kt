// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.testGuiFramework.driver.CheckboxTreeDriver
import com.intellij.testGuiFramework.fixtures.extended.ExtendedJTreePathFixture
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.util.FinderPredicate
import com.intellij.testGuiFramework.util.Predicate
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot
import org.fest.swing.timing.Pause

class CheckboxTreeFixture(
  checkboxTree: CheckboxTree,
  stringPath: List<String>,
  predicate: FinderPredicate = Predicate.equality,
  robot: Robot = GuiRobotHolder.robot,
  private val myDriver: CheckboxTreeDriver = CheckboxTreeDriver(robot)
) : ExtendedJTreePathFixture(checkboxTree, stringPath, predicate, robot, myDriver) {

  init {
    this.replaceDriverWith(myDriver)
  }

  fun clickCheckbox() = myDriver.clickCheckbox(target() as CheckboxTree, path)

  private fun getCheckboxComponent() = myDriver.getCheckboxComponent(target() as CheckboxTree, path)

  private fun setCheckboxValue(value: Boolean) {
    val maxNumberOfAttempts = 3
    var currentAttempt = maxNumberOfAttempts
    clickPath()
    while (currentAttempt > 0 && value != isSelected()) {
      clickCheckbox()
      currentAttempt--
      Pause.pause(500) // let's wait for animation of check drawing finishing
    }
    val actualValue = isSelected()
    assert(actualValue == value) {"Checkbox at path $path: actual value is $actualValue, but expected is $value, after ${maxNumberOfAttempts - currentAttempt} attempts"}
  }

  fun check() = setCheckboxValue(true)

  fun uncheck() = setCheckboxValue(false)

  fun isSelected(): Boolean = getCheckboxComponent()?.isSelected ?: false
}