// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D

@Experimental
abstract class CustomComponentInlayRenderer : EditorCustomElementRenderer {
  final override fun calcWidthInPixels(inlay: Inlay<*>): Int = 0
  final override fun calcHeightInPixels(inlay: Inlay<*>): Int = 0
  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) = Unit
  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) = Unit
}