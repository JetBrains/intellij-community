// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import java.awt.Graphics
import javax.swing.JComponent

/**
 * @author Alexander Lobas
 */
internal interface BorderPainter {
  fun paintAfterChildren(component: JComponent, g: Graphics)

  fun isPaintingOrigin(component: JComponent): Boolean
}

internal class DefaultBorderPainter : BorderPainter {
  override fun paintAfterChildren(component: JComponent, g: Graphics) {
  }

  override fun isPaintingOrigin(component: JComponent): Boolean = false
}

internal abstract class AbstractBorderPainter : BorderPainter {
  override fun isPaintingOrigin(component: JComponent): Boolean = true
}