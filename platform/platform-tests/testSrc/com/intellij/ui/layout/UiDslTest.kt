// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.testFramework.ProjectRule
import org.junit.After
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import javax.swing.JPanel
import javax.swing.JTable

/**
 * Set `test.update.snapshots=true` to automatically update snapshots if need.
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

  @After
  fun afterMethod() {
    System.clearProperty("idea.ui.comment.copyable")
  }

  private val dummyTextBinding = PropertyBinding({ "" }, {})

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
  fun hideableRow() {
    doTest {
      panel {
        row("Foo") {
          textField(dummyTextBinding)
        }
        hideableRow("Bar") {
          textField(dummyTextBinding)
        }
      }
    }
  }

  protected abstract fun doTest(panelCreator: () -> JPanel)
}