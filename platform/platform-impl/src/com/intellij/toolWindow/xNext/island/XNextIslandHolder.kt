// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.island

import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.ClientProperty
import fleet.util.logging.logger
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Paint
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border

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
    return IdeBackgroundUtil.getOriginalGraphics(super.getComponentGraphics(g))
  }

  override fun addImpl(comp: Component?, constraints: Any?, index: Int) {
    comp?.let {
      if (it is JComponent) {
        ClientProperty.putRecursive(it, IdeBackgroundUtil.NO_BACKGROUND, true)
      }
    }
    super.addImpl(comp, constraints, index)
  }

  override fun setBorder(border: Border?) {
    if (border !is XNextRoundedBorder) {
      logger<XNextIslandHolder>().warn {
        "Border type is invalid. Expected JRoundedCornerBorder, but received: ${border?.javaClass?.name ?: "null"}."
      }
      return
    }
    super.setBorder(border)
  }

  override fun isOpaque(): Boolean {
    return true
  }
}