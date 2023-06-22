// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.jetbrains.WindowDecorations.CustomTitleBar
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

open class HeaderClickTransparentListener(private val customTitleBar: CustomTitleBar): MouseAdapter() {
  protected fun hit() = customTitleBar.forceHitTest(false)
  override fun mouseClicked(e: MouseEvent) = hit()
  override fun mousePressed(e: MouseEvent) = hit()
  override fun mouseReleased(e: MouseEvent) = hit()
  override fun mouseEntered(e: MouseEvent) = hit()
  override fun mouseDragged(e: MouseEvent) = hit()
  override fun mouseMoved(e: MouseEvent) = hit()
}