// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.selected
import org.junit.Test
import javax.swing.JCheckBox
import kotlin.test.assertEquals

class RowsRangeTest {

  @Test
  fun testRecursiveEnabledAndVisible() {
    for (mode in RecursiveRowsRangeMode.values()) {
      RecursiveRowsRangeTester(mode, true).test()
      RecursiveRowsRangeTester(mode, false).test()
    }
  }
}

private enum class RecursiveRowsRangeMode {
  ROWS_RANGE,
  INDENT,
  BUTTONS_GROUP
}

/**
 * @param enabled true - check enabled, false - check visible
 */
private class RecursiveRowsRangeTester(private val mode: RecursiveRowsRangeMode, private val enabled: Boolean) {

  private lateinit var cbMain: JCheckBox
  private lateinit var checkBox1: JCheckBox
  private lateinit var checkBox2: JCheckBox

  init {
    panel {
      row {
        cbMain = checkBox("CheckBox 1").component
      }

      customRowsRange {
        row {
          checkBox1 = checkBox("CheckBox 2").component
        }
        customRowsRange {
          row {
            checkBox2 = checkBox("CheckBox 3").component
          }
        }.predicate(checkBox1.selected)
      }.predicate(cbMain.selected)
    }
  }

  fun test() {
    check()
    checkBox1.isSelected = true
    check()
    cbMain.isSelected = true
    check()
    cbMain.isSelected = false
    check()
    checkBox1.isSelected = false
    check()
    cbMain.isSelected = true
    check()
  }

  private fun Panel.customRowsRange(init: Panel.() -> Unit): RowsRange {
    return when (mode) {
      RecursiveRowsRangeMode.ROWS_RANGE -> rowsRange { init() }
      RecursiveRowsRangeMode.INDENT -> indent { init() }
      RecursiveRowsRangeMode.BUTTONS_GROUP -> buttonsGroup { init() }
    }
  }

  private fun RowsRange.predicate(predicate: ComponentPredicate) {
    if (enabled) {
      enabledIf(predicate)
    }
    else {
      visibleIf(predicate)
    }
  }

  private fun check() {
    assertEquals(cbMain.isEnabled, true)
    assertEquals(cbMain.isVisible, true)

    val group1Value = cbMain.isSelected
    val group2Value = cbMain.isSelected && checkBox1.isSelected

    if (enabled) {
      assertEquals(checkBox1.isEnabled, group1Value)
      assertEquals(checkBox1.isVisible, true)

      assertEquals(checkBox2.isEnabled, group2Value)
      assertEquals(checkBox2.isVisible, true)
    }
    else {
      assertEquals(checkBox1.isEnabled, true)
      assertEquals(checkBox1.isVisible, group1Value)

      assertEquals(checkBox2.isEnabled, true)
      assertEquals(checkBox2.isVisible, group2Value)
    }
  }
}
