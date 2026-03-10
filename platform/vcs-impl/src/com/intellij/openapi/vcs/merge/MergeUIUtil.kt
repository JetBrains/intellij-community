// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

object MergeUIUtil {
  fun installPopupAutoCloseOnResize(owner: JComponent): ComponentAdapter? {
    val window = SwingUtilities.getWindowAncestor(owner) ?: return null

    val listener = object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent) = cancelChildPopups()
      override fun componentResized(e: ComponentEvent) = cancelChildPopups()
      private fun cancelChildPopups() {
        JBPopupFactory.getInstance().getChildPopups(window).forEach { it.cancel() }
      }
    }
    window.addComponentListener(listener)
    return listener
  }
}
