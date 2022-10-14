// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.doLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.junit.Test
import kotlin.test.assertEquals

class SubPanelsTest {

  @Test
  fun testFillAligned() {
    for (horizontalAlign in HorizontalAlign.values()) {
      testAligned(horizontalAlign)
    }
  }

  private fun testAligned(horizontalAlign: HorizontalAlign) {
    lateinit var textField: JBTextField
    lateinit var textFieldFromSubPanel: JBTextField
    val subPanel = panel {
      row {
        textFieldFromSubPanel = textField()
          .horizontalAlign(horizontalAlign)
          .component
      }
    }

    val panel = panel {
      row("Row") {
        textField = textField()
          .horizontalAlign(horizontalAlign)
          .component
      }
      row("Row 2") {
        cell(subPanel)
          .align(AlignX.FILL)
      }
    }

    doLayout(panel)
    doLayout(subPanel, subPanel.width, subPanel.height)

    assertEquals(textField.size, textFieldFromSubPanel.size)
    assertEquals(subPanel.x + textFieldFromSubPanel.x, textField.x)
  }
}
