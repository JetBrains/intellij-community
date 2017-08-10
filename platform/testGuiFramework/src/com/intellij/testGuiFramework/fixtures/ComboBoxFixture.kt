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

import org.fest.swing.core.Robot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.fixture.JComboBoxFixture
import javax.swing.JButton
import javax.swing.JComboBox

class ComboBoxFixture(robot: Robot, comboBox: JComboBox<*>) : JComboBoxFixture(robot, comboBox) {

  fun expand(): ComboBoxFixture {
    val arrowButton = target().components.filter { it is JButton }.firstOrNull() ?: throw ComponentLookupException("Unable to find bounded arrow button for a combobox")
    robot().click(arrowButton)
    return this
  }

}