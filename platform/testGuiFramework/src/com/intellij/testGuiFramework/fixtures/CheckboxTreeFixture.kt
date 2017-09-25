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

class CheckboxTreeFixture(robot: Robot, checkboxTree: CheckboxTree): ExtendedTreeFixture(robot, checkboxTree) {

  init {
    this.replaceDriverWith(CheckboxTreeDriver(robot))
  }

  fun clickCheckbox(vararg pathStrings: String) {
    (this.driver() as CheckboxTreeDriver).clickCheckbox(target() as CheckboxTree, pathStrings.toList())
  }

}