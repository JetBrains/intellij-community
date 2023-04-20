// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import java.awt.*

internal class AdjustableSizeCardLayout: CardLayout() {
  override fun preferredLayoutSize(parent: Container): Dimension {
    val current = findCurrentComponent(parent)
    if (current != null) {
      val insets: Insets = parent.getInsets()
      val pref: Dimension = current.preferredSize

      if (pref.height < current.minimumSize.height) pref.height = current.minimumSize.height
      if (pref.width < current.minimumSize.width) pref.width = current.minimumSize.width

      pref.width += insets.left + insets.right
      pref.height += insets.top + insets.bottom
      return pref
    }
    return super.preferredLayoutSize(parent)
  }

  private fun findCurrentComponent(parent: Container): Component? {
    for (comp in parent.getComponents()) {
      if (comp.isVisible) {
        return comp
      }
    }
    return null
  }
}