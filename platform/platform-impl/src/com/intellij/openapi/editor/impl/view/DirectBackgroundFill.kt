// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Fills each background at once with a straight rectangle at the device pixels.
 * It fills no background that [defaultBackgrounds] gives as a default one.
 */
internal class DirectBackgroundFill(
  private val graphics: Graphics2D,
  private val defaultBackgrounds: DefaultBackgrounds,
) : BackgroundFill {
  /**
   * Fills the block with the one layer that the user sees.
   */
  override fun fillLayeredBlock(block: Rectangle2D, layers: List<BackgroundLayer>, count: Int) {
    fill(block, topOf(layers, count).color)
  }

  override fun fillBlock(block: Rectangle2D, layer: BackgroundLayer) {
    fill(block, layer.color)
  }

  /**
   * Fills the stub only when the layer that the user sees hugs. The fill of the line already shows a filling one.
   */
  override fun fillStub(block: Rectangle2D, layers: List<BackgroundLayer>, count: Int) {
    val top = topOf(layers, count)
    if (top.hugs) {
      fill(block, top.color)
    }
  }

  override fun fillLineExtensionBackground(block: Rectangle2D, color: Color) {
    fill(block, color)
  }

  override fun fillAfterLineEndBox(block: Rectangle2D, layer: BackgroundLayer) {
    fill(block, layer.color)
  }

  override fun paintCollected(graphics: Graphics2D, lineHeight: Int) {
  }

  /**
   * Gives the one layer that the user sees in a block of the first [count] of these layers.
   * A layer that forces the background wins over every other layer.
   */
  private fun topOf(layers: List<BackgroundLayer>, count: Int): BackgroundLayer {
    var top = layers[0]
    for (index in 1 until count) {
      val layer = layers[index]
      if (layer.forcesBackground && layer > top) {
        top = layer
      }
    }
    return top
  }

  private fun fill(block: Rectangle2D, color: Color) {
    if (block.width <= 0 || defaultBackgrounds.isDefault(color)) {
      return
    }
    EditorPainter.fillRectExact(graphics, block, color)
  }
}
