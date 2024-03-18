// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

internal class LongTextsPanel: UISandboxPanel {

  override val title: String = "Long texts"

  override val isScrollbarNeeded: Boolean = false

  override fun createContent(disposable: Disposable): JComponent {
    val times = 20

    return panel {
      row {
        text("WordWrapInsideWordsIsSupported:" + "NoSpace".repeat(times))
      }

      row {
        text("WordWrapInsideWordsIsSupported:" + ("NoSpace".repeat(20) + " ").repeat(5) + "End")
      }

      group("Word Wrap") {
        row {
          text("Text ".repeat(times))
        }
        row {
          comment("Comment ".repeat(times))
        }
        row {
          textField()
        }.rowComment("RowComment ".repeat(times), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
        row {
          textField()
            .comment("CellComment ".repeat(times), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
        }
      }

      group("Align Right Shouldn't Wrap") {
        row {
          text("Right aligned text")
            .align(AlignX.RIGHT)
        }
        row {
          comment("Right aligned comment")
            .align(AlignX.RIGHT)
        }
      }
    }.apply {
      minimumSize = JBDimension(200, 100)
      preferredSize = JBDimension(200, 100)
    }
  }
}