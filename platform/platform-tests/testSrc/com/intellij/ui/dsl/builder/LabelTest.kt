// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.UiDslException
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.assertEquals

class LabelTest {

  @Test
  fun testLabelFor() {
    val label = JLabel("Label:")
    lateinit var component: JTextField
    panel {
      row(label) {
        component = textField().component
      }
    }

    assertEquals(label.labelFor, component)
  }

  @Test
  fun testLabelForProperty() {
    val label = JLabel("Label:")
    val panel = JPanel()
    val textField = JTextField()
    panel.add(textField)
    panel.putClientProperty(DslComponentProperty.LABEL_FOR, textField)
    panel {
      row(label) {
        cell(panel)
      }
    }

    assertEquals(label.labelFor, textField)
  }

  @Test
  fun testInvalidLabelForProperty() {
    val label = JLabel("Label:")
    val panel = JPanel()
    panel.putClientProperty(DslComponentProperty.LABEL_FOR, "Invalid")
    assertThrows<UiDslException> {
      panel {
        row(label) {
          cell(panel)
        }
      }
    }
  }
}