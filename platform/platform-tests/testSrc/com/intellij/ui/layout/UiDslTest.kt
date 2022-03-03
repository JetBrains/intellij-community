// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ProjectRule
import org.junit.*
import org.junit.rules.TestName
import javax.swing.JPanel
import javax.swing.JTable

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if needed.
 */
abstract class UiDslTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ProjectRule()

    init {
      System.setProperty("idea.ui.set.password.echo.char", "true")
    }
  }

  @Rule
  @JvmField
  val testName = TestName()

  @Before
  fun initializeLAF() {
    LafManager.getInstance()
  }

  @After
  fun afterMethod() {
    System.clearProperty("idea.ui.comment.copyable")
  }

  @Test
  fun `field with gear`() {
    doTest { fieldWithGear() }
  }

  @Test
  fun `field with gear with indent`() {
    doTest { fieldWithGearWithIndent() }
  }

  @Test
  fun `align fields in the nested grid`() {
    doTest { alignFieldsInTheNestedGrid() }
  }

  @Test
  fun `align fields`() {
    doTest { labelRowShouldNotGrow() }
  }

  @Test
  fun cell() {
    doTest { cellPanel() }
  }

  @Test
  fun `note row in the dialog`() {
    doTest { noteRowInTheDialog() }
  }

  @Test
  fun `visual paddings`() {
    doTest { visualPaddingsPanel() }
  }

  @Test
  fun `vertical buttons`() {
    doTest { withVerticalButtons() }
  }

  @Test
  fun `single vertical button`() {
    doTest { withSingleVerticalButton() }
  }

  @Test
  fun `do not add visual paddings for titled border`() {
    doTest { commentAndPanel() }
  }

  @Test
  fun `checkbox that acts as label`() {
    doTest { checkBoxFollowedBySpinner() }
  }

  @Test
  fun `titled rows`() {
    doTest { titledRows() }
  }

  @Test
  fun `titled row`() {
    doTest { titledRow() }
  }

  @Test
  fun `sample configurable panel`() {
    doTest { sampleConfigurablePanel() }
  }

  @Test
  fun scrollPaneNoGrow() {
    doTest {
      panel {
        row {
          scrollPane(JTable())
        }
        row {
          scrollPane(JTable()).noGrowY()
        }
      }
    }
  }

  @Test
  fun subRowsIndent() {
    doTest { rowWithIndent() }
  }

  @Test
  fun `checkbox rows with big components`() {
    Assume.assumeFalse("ComboBoxes in MacOs LaF have different border insets, that are used to build layout constraints", SystemInfo.isMac)
    doTest { checkboxRowsWithBigComponents() }
  }

  protected abstract fun doTest(panelCreator: () -> JPanel)
}