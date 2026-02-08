// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.island

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.islands.isColorIslandGradient
import com.intellij.ui.BorderPainter
import com.intellij.ui.DefaultBorderPainter
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Paint
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Experimental
@ApiStatus.Internal
class XNextIslandHolder : JPanel() {
  companion object {
    @JvmStatic
    fun createToolWindowIsland(fillColor: (c: JComponent) -> Paint?): JComponent = XNextIslandHolder().apply {
      border = XNextRoundedBorder.createIslandBorder(fillColor)
    }

    @JvmStatic
    fun createNewSolutionIsland(fillColor: (c: JComponent) -> Paint?): JComponent = XNextIslandHolder().apply {
      border = XNextRoundedBorder.createNewSolutionIslandBorder(fillColor)
    }
  }

  override fun getComponentGraphics(g: Graphics?): Graphics? {
    return InternalUICustomization.getInstance()?.backgroundImageGraphics(this, super.getComponentGraphics(g)) ?: g
  }

  override fun addImpl(comp: Component?, constraints: Any?, index: Int) {
    if (!isColorIslandGradient()) {
      comp?.let {
        if (it is JComponent) {
          InternalUICustomization.getInstance()?.installEditorBackground(it)
        }
      }
    }
    super.addImpl(comp, constraints, index)
  }

  override fun isOpaque(): Boolean {
    return true
  }

  internal var borderPainter: BorderPainter = DefaultBorderPainter()

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)
    borderPainter.paintAfterChildren(this, g)
  }

  override fun isPaintingOrigin(): Boolean {
    return borderPainter.isPaintingOrigin(this)
  }
}