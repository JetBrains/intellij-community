// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JRadioButton

/**
 * @author yole
 */
class UiDslBindingsTest : BasePlatformTestCase() {
  private var x = false

  fun testRadioButtonReset() {
    val dialogPanel = panel {
      row {
        buttonGroup {
          radioButton("Foo", ::x)
          radioButton("Bar")
        }
      }
    }
    dialogPanel.reset()
    val radioButtons = dialogPanel.components.filterIsInstance<JRadioButton>()
    assertTrue(radioButtons[1].isSelected)
  }
}
