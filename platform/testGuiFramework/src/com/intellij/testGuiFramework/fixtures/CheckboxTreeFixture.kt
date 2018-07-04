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

import com.intellij.testGuiFramework.driver.CheckboxTreeDriver
import com.intellij.testGuiFramework.fixtures.extended.ExtendedTreeFixture
import com.intellij.ui.CheckboxTree
import org.fest.swing.core.Robot

class CheckboxTreeFixture(robot: Robot, checkboxTree: CheckboxTree) : ExtendedTreeFixture(robot, checkboxTree) {

  init {
    this.replaceDriverWith(CheckboxTreeDriver(robot))
  }

  fun clickCheckbox(vararg pathStrings: String) {
    (this.driver() as CheckboxTreeDriver).clickCheckbox(target() as CheckboxTree, pathStrings.toList())
  }

  fun getCheckboxComponent(vararg pathStrings: String) = (this.driver() as CheckboxTreeDriver).getCheckboxComponent(
    target() as CheckboxTree, pathStrings.toList())

  fun setCheckboxValue(value: Boolean, vararg pathStrings: String) {
    val checkbox = getCheckboxComponent(*pathStrings)
    if (checkbox != null && checkbox.isSelected != value) {
      clickCheckbox(*pathStrings)
    }
  }

  /**
   * Sometimes one click doesn't work - e.g. it can be swallowed by scrolling
   * Then the second click must help.
   * */
  private fun ensureCheckboxValue(value: Boolean, mainPathString: Array<out String>, reservePathStrings: Array<out String>) {
    setCheckboxValue(value, *mainPathString)
    setCheckboxValue(value, *reservePathStrings)
  }

  fun check(vararg pathStrings: String) {
    ensureCheckboxValue(true, pathStrings, pathStrings)
  }

  fun uncheck(vararg pathStrings: String) = ensureCheckboxValue(false, pathStrings, pathStrings)

  /**
   * Sometimes checkbox can change its text after being clicked (e.g. "JBoss Drools" after selecting becomes "JBoss Drools (6.2.0)")
   * in such cases the changed path specify in the parameter `reservePathStrings`
   * */
  fun checkWithReserve(mainPathStrings: Array<out String>, reservePathStrings: Array<out String>) {
    ensureCheckboxValue(true, mainPathStrings, reservePathStrings)
  }

  fun uncheckWithReserve(mainPathStrings: Array<out String>, reservePathStrings: Array<out String>) =
    ensureCheckboxValue(false, mainPathStrings, reservePathStrings)
}