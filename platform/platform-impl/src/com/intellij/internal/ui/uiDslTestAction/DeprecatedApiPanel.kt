// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class DeprecatedApiPanel {

  val panel = panel {
    row {
      label("Row with comment")
    }.rowComment("Row <b>comment</b>")
    // Unhide deprecated rowComment() method to check
    // }.rowComment("<html>Row <b>comment</b>")

    row {
      comment("Deprecated <b>comment</b>")
      // Unhide deprecated comment() method to check
      // comment("<html>Deprecated <b>comment</b>")
    }

    row {
      textField()
        .comment("Cell <b>comment</b>")
      // Unhide deprecated comment() method to check
      // .comment("<html>Cell <b>comment</b>")
    }
  }
}
