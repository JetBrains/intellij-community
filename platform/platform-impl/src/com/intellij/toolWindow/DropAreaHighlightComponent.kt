// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ui.components.panels.NonOpaquePanel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics

/**
 * Shows a drop placeholder of a tool window drag. It fills its bounds with [fillColor], or paints nothing for `null`.
 */
@ApiStatus.Internal
class DropAreaHighlightComponent(private val fillColor: () -> Color?) : NonOpaquePanel() {
  override fun paint(g: Graphics) {
    g.color = fillColor() ?: return
    g.fillRect(0, 0, width, height)
  }
}