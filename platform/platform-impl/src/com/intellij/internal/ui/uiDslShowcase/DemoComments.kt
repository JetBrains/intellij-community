// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslShowcase

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text

@Demo(title = "Comments",
  description = "Comment is a gray (depends on color scheme) text which can be assigned to cell (placed under cell), rows or placed in any cell directly")
fun demoComments(): DialogPanel {
  return panel {
    row {
      textField()
        .comment("Comment to textField1")
        .text("textField1")
      textField()
        .text("textField2")
      textField()
        .comment("Comment to textField3")
        .text("textField3")
    }

    row("Row2:") {
      textField()
    }.rowComment("Row2 comment is placed under the row")

    row("Row3:") {
      comment("Free comment")
    }

    row("Row4:") {
      commentNoWrap("Free comment that is not wrapped when contains a very long text, which exceeds available space in the window. To check behaviour reduce size of this dialog")
    }

    row("Row5:") {
      comment("Comments can be an html text with some clickable <a href='link'>link</a> and even <a href='several links'>several links</a>") {
        Messages.showMessageDialog("Link '${it.description}' is clicked", "Message", null)
      }
    }
  }
}
