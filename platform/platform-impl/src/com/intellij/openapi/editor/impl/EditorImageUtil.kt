// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBSwingUtilities
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

internal object EditorImageUtil {
  @JvmStatic
  fun createEditorImage(editor: EditorImpl, width: Int, height: Int): BufferedImage =
    createEditorImage(editor, width.toDouble(), height.toDouble())

  @JvmStatic
  fun createEditorImage(editor: EditorImpl, width: Double, height: Double): BufferedImage =
    ImageUtil.createImage(
      ScaleContext.create(editor.contentComponent),
      width.coerceAtLeast(1.0),
      height.coerceAtLeast(1.0),
      // No ARGB, see sun.java2d.SurfaceData.canRenderLCDText, it requires no transparency for subpixel AA!
      BufferedImage.TYPE_INT_RGB,
      PaintUtil.RoundingMode.CEIL,
    )

  @JvmStatic
  fun createImageGraphics(editor: EditorImpl, image: BufferedImage, bounds: Rectangle2D): Graphics2D {
    val imageGraphics = image.createGraphics()
    imageGraphics.translate(-bounds.x, -bounds.y)
    val graphics = JBSwingUtilities.runGlobalCGTransform(editor.contentComponent, imageGraphics)
    EditorUIUtil.setupEditorPainting(graphics, editor.useEditorAntialiasing())
    graphics.clip(bounds)
    return graphics
  }
}
