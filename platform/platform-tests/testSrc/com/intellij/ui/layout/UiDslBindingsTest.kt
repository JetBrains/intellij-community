// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JTextField


class UiDslBindingsTest : BasePlatformTestCase() {
  private var stringValue = ""

  fun testApplyIfEnabled() {
    lateinit var textField: JTextField
    stringValue = ""
    val dialogPanel = panel {
      row {
        textField(::stringValue).applyIfEnabled().also { textField = it.component }
      }
    }
    textField.text = "abc"
    dialogPanel.apply()
    assertEquals("abc", stringValue)
    textField.isEnabled = false
    textField.text = "def"
    dialogPanel.apply()
    assertEquals("abc", stringValue)
  }
}
