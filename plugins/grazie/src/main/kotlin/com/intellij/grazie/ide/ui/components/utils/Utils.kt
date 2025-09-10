package com.intellij.grazie.ide.ui.components.utils

import kotlinx.html.BODY
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import javax.swing.JComponent

internal fun <T : JComponent> T.configure(configure: T.() -> Unit): T {
  this.configure()
  return this
}

fun html(body: BODY.() -> Unit) = createHTML(false).html { body { body(this) } }

