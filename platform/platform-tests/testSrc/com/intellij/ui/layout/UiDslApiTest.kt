// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.assertNotEquals

class UiDslApiTest : BasePlatformTestCase() {

  fun testLabelFor_Text() {
    lateinit var component: JComponent
    val panel = panel {
      row("Label") {
        component = comboBox(DefaultComboBoxModel(), { "" }, {}).component
      }
    }

    assertEquals(component, findLabel(panel)?.labelFor)
  }

  fun testLabelFor_JLabel() {
    lateinit var component: JComponent
    val panel = panel {
      row(JLabel("Label")) {
        component = textField({ "" }, {}).component
      }
    }

    assertEquals(component, findLabel(panel)?.labelFor)
  }

  fun testLabelFor_SeveralComponents() {
    lateinit var component: JComponent
    val panel = panel {
      row("Label") {
        component = textField({ "" }, {}).component
        label("L")
        label("A")
      }
    }

    assertEquals(component, findLabel(panel)?.labelFor)
  }

  fun testLabelFor_WrongComponent() {
    lateinit var component: JLabel
    val panel = panel {
      row("Label") {
        component = label("Not labelFor").component
      }
    }

    val label = findLabel(panel)
    assertNotEquals(label, component)
    assertNull(label?.labelFor)
  }

  private fun findLabel(panel: DialogPanel): JLabel? {
    for (component in panel.components) {
      if (component is JLabel) {
        return component
      }
    }

    return null
  }
}
