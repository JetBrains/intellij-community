// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text

@Demo(title = "demo.comments.title",
      description = "demo.comments.description",
      scrollbar = true)
fun demoComments(): DialogPanel {
  return panel {
    group(DevkitUiDslBundle.message("demo.comments.group.cell")) {
      row {
        text(DevkitUiDslBundle.message("demo.comments.cell.text"))
      }

      row {
        textField()
          .text(DevkitUiDslBundle.message("demo.comments.text.field1"))
          .commentRight(DevkitUiDslBundle.message("demo.comments.text.field1.right"))
        textField()
          .text(DevkitUiDslBundle.message("demo.comments.text.field2"))
          .comment(DevkitUiDslBundle.message("demo.comments.text.field2.bottom"))
        textField()
          .text(DevkitUiDslBundle.message("demo.comments.text.field3"))
          .contextHelp(DevkitUiDslBundle.message("demo.comments.text.field3.context.help"))
      }
    }

    group(DevkitUiDslBundle.message("demo.comments.group.row")) {
      row(DevkitUiDslBundle.message("demo.comments.label")) {
        textField()
      }.rowComment(DevkitUiDslBundle.message("demo.comments.row.comment"))
    }

    group(DevkitUiDslBundle.message("demo.comments.group.arbitrary")) {
      row {
        comment(DevkitUiDslBundle.message("demo.comments.arbitrary"))
      }
    }

    group(DevkitUiDslBundle.message("demo.comments.group.common")) {
      row {
        comment(DevkitUiDslBundle.message("demo.comments.html.comment")) {
          Messages.showMessageDialog(
            DevkitUiDslBundle.message("demo.comments.link.clicked", it.description),
            DevkitUiDslBundle.message("demo.comments.message"),
            null,
          )
        }
      }

      val longString = (1..8).joinToString { DevkitUiDslBundle.message("demo.comments.long.string") }

      row {
        comment(DevkitUiDslBundle.message("demo.comments.word.wrap", longString))
      }


      row {
        comment(DevkitUiDslBundle.message("demo.comments.no.wrap"), maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
      }

      row {
        comment(DevkitUiDslBundle.message("demo.comments.max.line.length", longString), maxLineLength = 60)
      }
    }
  }
}
