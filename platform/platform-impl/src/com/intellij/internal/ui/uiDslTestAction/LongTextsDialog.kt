// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.panel
import javax.swing.Action
import javax.swing.JComponent

internal class LongTextsDialog : DialogWrapper(false) {

  init {
    init()
    title = "Long Texts"
    setOKButtonText("Close")
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction)
  }

  override fun createCenterPanel(): JComponent {
    val times = 50

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
    }
  }
}
