// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.dsl

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
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

    @Suppress("HardCodedStringLiteral")
    return panel {
      row {
        text("WordWrapInsideWordsIsSupported:" + "NoSpace".repeat(times))
      }

      row {
        text("WordWrapInsideWordsIsSupported:" + ("NoSpace".repeat(20) + " ").repeat(5) + "End")
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.word.wrap")) {
        row {
          @Suppress("HardCodedStringLiteral")
          text(DevkitUiDslBundle.message("sandbox.label.text").repeat(times))
        }
        row {
          @Suppress("HardCodedStringLiteral")
          comment(DevkitUiDslBundle.message("sandbox.text.comment").repeat(times))
        }
        row {
          textField()
        }.rowComment("RowComment ".repeat(times), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
        row {
          textField()
            .comment("CellComment ".repeat(times), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
        }
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.align.right.shouldn.t.wrap")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.label.right.aligned.text"))
            .align(AlignX.RIGHT)
        }
        row {
          comment(DevkitUiDslBundle.message("sandbox.text.right.aligned.comment"))
            .align(AlignX.RIGHT)
        }
      }
    }.apply {
      minimumSize = JBDimension(200, 100)
      preferredSize = JBDimension(200, 100)
    }
  }
}