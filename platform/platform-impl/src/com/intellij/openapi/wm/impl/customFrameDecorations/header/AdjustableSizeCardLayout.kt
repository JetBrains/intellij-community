// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension

// todo is it needed
internal class AdjustableSizeCardLayout(private val heightProvider: () -> Int) : BorderLayout() {
  override fun preferredLayoutSize(parent: Container): Dimension {
    val current = parent.getComponent(0)
    val insets = parent.insets
    val size = current.preferredSize
    if (size.width < current.minimumSize.width) {
      size.width = current.minimumSize.width
    }

    size.width += insets.left + insets.right
    size.height = heightProvider()
    return size
  }
}