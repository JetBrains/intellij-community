package com.intellij.grazie.ide.ui.components.utils

import javax.swing.JComponent

internal fun <T : JComponent> T.configure(configure: T.() -> Unit): T {
  this.configure()
  return this
}

