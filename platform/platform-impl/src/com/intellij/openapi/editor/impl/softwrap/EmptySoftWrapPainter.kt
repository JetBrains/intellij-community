// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics

@ApiStatus.Internal
@ApiStatus.Experimental
object EmptySoftWrapPainter : SoftWrapPainter {
  override fun paint(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int): Int = 0

  override fun getDrawingHorizontalOffset(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int): Int = 0

  override fun getMinDrawingWidth(drawingType: SoftWrapDrawingType): Int = 0

  override fun canUse(): Boolean = true

  override fun reinit() {}
}