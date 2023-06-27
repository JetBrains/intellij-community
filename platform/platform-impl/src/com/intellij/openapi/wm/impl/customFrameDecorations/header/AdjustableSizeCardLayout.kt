// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension

// todo is it needed
internal class AdjustableSizeCardLayout : BorderLayout() {
  override fun preferredLayoutSize(parent: Container): Dimension {
    val current = parent.getComponent(0) ?: return super.preferredLayoutSize(parent)
    val insets = parent.insets
    val pref = current.preferredSize

    if (pref.height < current.minimumSize.height) {
      pref.height = current.minimumSize.height
    }
    if (pref.width < current.minimumSize.width) {
      pref.width = current.minimumSize.width
    }

    pref.width += insets.left + insets.right
    pref.height += insets.top + insets.bottom
    return pref
  }
}