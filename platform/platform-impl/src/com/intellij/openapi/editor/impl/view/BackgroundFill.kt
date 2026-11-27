// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Fills the backgrounds of one paint of the editor.
 *
 * [DirectBackgroundFill] fills each background at once.
 * [HighlighterVisualModel] keeps the backgrounds and fills them in [paintCollected].
 */
internal interface BackgroundFill {
  /**
   * Fills one block with several [layers]. The list starts at the top layer.
   */
  fun fillLayeredBlock(block: Rectangle2D, layers: List<BackgroundLayer>)

  /**
   * Fills one block with one [layer].
   */
  fun fillBlock(block: Rectangle2D, layer: BackgroundLayer)

  /**
   * Fills one block with the background of the selection.
   */
  fun fillSelectionBlock(block: Rectangle2D)

  /**
   * Fills the background of a line extension.
   */
  fun fillLineExtensionBackground(block: Rectangle2D, color: Color)

  /**
   * Fills the box of a background after the end of a line.
   */
  fun fillAfterLineEndBox(block: Rectangle2D, layer: BackgroundLayer)

  /**
   * Fills the backgrounds that the fill keeps.
   */
  fun paintCollected(graphics: Graphics2D, lineHeight: Int)
}
