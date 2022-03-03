// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.ui.dsl.builder.buttonGroup
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
      labelHtml("<html>labelHtml with a <a>link</a>")
    }

    row {
      comment("Deprecated <b>comment</b>")
      // Unhide deprecated comment() method to check
      // comment("<html>Deprecated <b>comment</b>")
    }

    row {
      commentNoWrap((1..10).joinToString(" ") { "commentNoWrap" })
    }

    row {
      textField()
        .comment("Cell <b>comment</b>")
      // Unhide deprecated comment() method to check
      // .comment("<html>Cell <b>comment</b>")
    }

    row {
      textField()
        .commentHtml("<html>Html cell <b>comment</b>")
    }

    group("Deprecated Group", topGroupGap = true) {
      row { textField() }
    }

    collapsibleGroup("Deprecated collapsibleGroup", topGroupGap = true) {
      row { textField() }
    }.expanded = true

    buttonGroup {
      row {
        // Unhide deprecated radioButton() method to check
        radioButton("Value 1")
        radioButton("Value 2")
      }
    }

    var boolean = true
    buttonGroup({ boolean }, { boolean = it }) {
      row {
        radioButton("Value true", true)
        radioButton("Value false", false)
      }
    }

    row {
      comboBox((1..5).map { "Item $it" }.toTypedArray())
    }
  }
}
