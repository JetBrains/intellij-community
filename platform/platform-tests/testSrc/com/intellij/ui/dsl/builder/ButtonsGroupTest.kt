// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.UiDslException
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import javax.swing.JRadioButton
import kotlin.reflect.KMutableProperty0
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonsGroupTest {

  enum class Enum1 {
    VALUE1,
    VALUE2
  }

  /**
   * Enum with overriding, so Enum2 class and Enum2.VALUE1 class are different
   */
  enum class Enum2(val id: Int) {
    VALUE1(1) {
      override fun toString(): String {
        return "$id"
      }
    },
    VALUE2(2) {
      override fun toString(): String {
        return "$id"
      }
    }
  }

  var boolean = true
  var int = 1
  var string = "a"
  var enum1 = Enum1.VALUE1
  var enum2 = Enum2.VALUE1

  @Test
  fun testInvalidGroups() {
    assertThrows<UiDslException> {
      testButtonsGroup(1, 2)
    }
    assertThrows<UiDslException> {
      testButtonsGroup("1", "2", ::int)
    }
    assertThrows<UiDslException> {
      testButtonsGroup(null, null, ::int)
    }
  }

  @Test
  fun testValidGroups() {
    testButtonsGroup(null, null)
    testButtonsGroup(true, false, ::boolean)
    testButtonsGroup(1, 2, ::int)
    testButtonsGroup("b", "c", ::string)
    testButtonsGroup(Enum1.VALUE1, Enum1.VALUE2, ::enum1)
    testButtonsGroup(Enum2.VALUE1, Enum2.VALUE2, ::enum2)
  }

  @Test
  fun testSelectionChange() {
    var rb1 = JRadioButton()
    var rb2 = JRadioButton()
    panel {
      buttonsGroup {
        row { rb1 = radioButton("1").component }
        row { rb2 = radioButton("2").selected(true).component }
      }
    }

    checkChangeSelection(rb1, rb2)

    panel {
      buttonsGroup {
        row { rb1 = cell(JRadioButton("1")).component }
        row { rb2 = cell(JRadioButton("2")).selected(true).component }
      }
    }

    checkChangeSelection(rb1, rb2)
  }

  /**
   *  [rb2] is selected by default
   */
  private fun checkChangeSelection(rb1: JRadioButton, rb2: JRadioButton) {
    assertFalse(rb1.isSelected)
    assertTrue(rb2.isSelected)

    rb1.isSelected = true

    assertTrue(rb1.isSelected)
    assertFalse(rb2.isSelected)
  }

  @Test
  fun testGroupsWithSelected() {
    checkButtonsGroupWithSelected(-1, -1, false, false)
    checkButtonsGroupWithSelected(1, -1, true, false)
    checkButtonsGroupWithSelected(2, -1, false, true)
    checkButtonsGroupWithSelected(-1, 1, true, false)
    checkButtonsGroupWithSelected(-1, 2, false, true)
    checkButtonsGroupWithSelected(1, 1, true, false)
    checkButtonsGroupWithSelected(1, 2, true, false)
    checkButtonsGroupWithSelected(2, 1, false, true)
    checkButtonsGroupWithSelected(2, 2, false, true)
  }

  private fun checkButtonsGroupWithSelected(initValue: Int, selectedValue: Int,
                                            expectedRadioButton1Selected: Boolean, expectedRadioButton2Selected: Boolean) {
    val radioButtons = mutableListOf<JBRadioButton>()
    int = initValue
    panel {
      buttonsGroup {
        for (i in 1..2) {
          row {
            radioButtons.add(radioButton("radioButton$i", i)
              .applyToComponent {
                if (selectedValue == i) {
                  isSelected = true
                }
              }
              .component)
          }
        }
      }.bind(::int)
    }
    assertEquals(radioButtons[0].isSelected, expectedRadioButton1Selected)
    assertEquals(radioButtons[1].isSelected, expectedRadioButton2Selected)
  }

  private fun testButtonsGroup(value1: Any?, value2: Any?) {
    panel {
      buttonsGroup(value1, value2)
    }
  }

  private inline fun <reified T : Any> testButtonsGroup(value1: Any?, value2: Any?, prop: KMutableProperty0<T>) {
    panel {
      buttonsGroup(value1, value2)
        .bind(prop)
    }
  }

  private fun Panel.buttonsGroup(value1: Any?, value2: Any?): ButtonsGroup {
    return buttonsGroup {
      row {
        radioButton("radioButton1", value1)
      }
      row {
        radioButton("radioButton2", value2)
      }
    }
  }
}
