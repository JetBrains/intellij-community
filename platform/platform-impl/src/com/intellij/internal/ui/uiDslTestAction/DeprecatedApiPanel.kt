// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class DeprecatedApiPanel {

  val panel = panel {
    // Unhide deprecated rowComment() method to check
    row {
      label("Row with comment")
    }.rowComment("<html>Row <b>comment</b>")

    row {
      labelHtml("<html>labelHtml with a <a>link</a>")
    }

    row {
      // Unhide deprecated comment() method to check
      comment("<html>Deprecated <b>comment</b>")
    }

    row {
      commentNoWrap((1..10).joinToString(" ") { "commentNoWrap" })
    }

    row {
      textField()
        // Unhide deprecated comment() method to check
        .comment("<html>Cell <b>comment</b>")
    }

    row {
      textField()
        .commentHtml("<html>Html cell <b>comment</b>")
    }
  }
}
