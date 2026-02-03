// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnabledVisibleTest {

  @Test
  fun testNestedEnabled() {
    testNestedEnabledVisible({ entity, value ->
                               when (entity) {
                                 is Panel -> entity.enabled(value)
                                 is Row -> entity.enabled(value)
                                 is Cell<*> -> entity.enabled(value)
                                 else -> Unit
                               }
                             }, { jComponent -> jComponent.isEnabled })
  }

  @Test
  fun testNestedVisible() {
    testNestedEnabledVisible({ entity, value ->
                               when (entity) {
                                 is Panel -> entity.visible(value)
                                 is Row -> entity.visible(value)
                                 is Cell<*> -> entity.visible(value)
                                 else -> Unit
                               }
                             }, { jComponent -> jComponent.isVisible })
  }

  @Test
  fun testVisibleIf() {
    lateinit var checkBoxText: Cell<JBCheckBox>
    lateinit var checkBoxRow: Cell<JBCheckBox>
    lateinit var textField: Cell<JBTextField>
    lateinit var label: JLabel
    panel {
      row {
        checkBoxRow = checkBox("")
          .selected(true)
        checkBoxText = checkBox("")
          .selected(true)
      }

      row("visibleIf test row") {
        textField = textField()
          .commentRight("Right comment")
          .comment("Comment")
          .visibleIf(checkBoxText.selected)
        label = label("")
          .component
      }.visibleIf(checkBoxRow.selected)
    }

    assertTrue(label.isVisible)
    textField.assertVisible(true)

    checkBoxText.component.isSelected = false
    assertTrue(label.isVisible)
    textField.assertVisible(false)

    checkBoxText.component.isSelected = true
    assertTrue(label.isVisible)
    textField.assertVisible(true)

    checkBoxRow.component.isSelected = false
    assertFalse(label.isVisible)
    textField.assertVisible(false)

    checkBoxRow.component.isSelected = true
    assertTrue(label.isVisible)
    textField.assertVisible(true)
  }

  @Test
  fun testEnabledIf() {
    lateinit var checkBoxText: Cell<JBCheckBox>
    lateinit var checkBoxRow: Cell<JBCheckBox>
    lateinit var textField: Cell<JBTextField>
    lateinit var label: JLabel
    panel {
      row {
        checkBoxRow = checkBox("")
          .selected(true)
        checkBoxText = checkBox("")
          .selected(true)
      }

      row("enabledIf test row") {
        textField = textField()
          .commentRight("Right comment")
          .comment("Comment")
          .enabledIf(checkBoxText.selected)
        label = label("")
          .component
      }.enabledIf(checkBoxRow.selected)
    }

    assertTrue(label.isEnabled)
    textField.assertEnabled(true)

    checkBoxText.component.isSelected = false
    assertTrue(label.isEnabled)
    textField.assertEnabled(false)

    checkBoxText.component.isSelected = true
    assertTrue(label.isEnabled)
    textField.assertEnabled(true)

    checkBoxRow.component.isSelected = false
    assertFalse(label.isEnabled)
    textField.assertEnabled(false)

    checkBoxRow.component.isSelected = true
    assertTrue(label.isEnabled)
    textField.assertEnabled(true)
  }

  private fun Cell<JBTextField>.assertVisible(visible: Boolean) {
    assertEquals(component.isVisible, visible)
    assertEquals(commentRight?.isVisible, visible)
    assertEquals(comment?.isVisible, visible)
  }

  private fun Cell<JBTextField>.assertEnabled(enabled: Boolean) {
    assertEquals(component.isEnabled, enabled)
    assertEquals(commentRight?.isEnabled, enabled)
    assertEquals(comment?.isEnabled, enabled)
  }

  private fun testNestedEnabledVisible(setState: (entity: Any, value: Boolean) -> Unit, getState: (JComponent) -> Boolean) {
    val iterationCount = 100
    val entities = mutableListOf<Any>()
    lateinit var cell: Cell<JComponent>

    panel {
      entities += row {
        entities += panel {
          entities += group {
            entities += row {
              cell = textField()
              entities += cell
            }
          }
        }
      }
    }

    val states = Array(entities.size) { true }

    for (i in 1..iterationCount) {
      val index = Random.nextInt(states.size)
      val state = Random.nextBoolean()
      states[index] = state
      setState(entities[index], state)
      val expectedComponentState = states.all { it }
      assertEquals(expectedComponentState, getState(cell.component))
    }

    // Return all states to true
    for (i in states.indices) {
      states[i] = true
      setState(entities[i], true)
      val expectedComponentState = states.all { it }
      assertEquals(expectedComponentState, getState(cell.component))
    }
  }
}
