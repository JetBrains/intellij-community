// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.util.ui.GraphicsUtil
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.util.TreeMap

/**
 * The highlighter backgrounds of one paint.
 *
 * The painter creates the model only while the editor rounds the corners of the highlighting.
 * [BackgroundLayer] tells which backgrounds are rounded.
 * The painter adds one rectangle per background style. [paint] fills the styles from the bottom up.
 * One style gives one shape, also across visual lines. A rounded style fills one outline with antialiasing,
 * and a straight style fills its rectangles at the device pixels.
 * A box of a background after the end of a line is a shape of its own. [paintAfterLineEndBoxes] fills the boxes.
 * A background of a line extension is a straight rectangle of its own. [paint] fills it among the styles.
 * A rectangle that fills the line ends at the viewport edge, not at the clip edge.
 * A rectangle outside the clip of the paint gives the corners of its neighbors, and it paints nothing.
 * The model lives for one paint. It keeps no state for the next paint.
 */
internal class HighlighterVisualModel(
  private val editor: EditorImpl,
  private val yShift: Int,
  private val huggingHighlighting: Boolean,
) {
  private val rows = BackgroundRows()
  private val styleIds = TreeMap<BackgroundLayer, Int>(BackgroundLayer.STYLE_ORDER)
  private val styles = ArrayList<BackgroundLayer>()
  private val chain = IntArrayList()
  private val edges = LongOpenHashSet()
  private val boxRows = BackgroundRows()
  private val boxStyles = ArrayList<BackgroundLayer>()
  private val extensionRows = BackgroundRows()
  private val extensionColors = ArrayList<Color>()

  /**
   * Gives for each background of [addLineExtensionBackground] the number of rows that the painter added before it.
   */
  private val rowCountsBeforeExtensions = IntArrayList()

  fun paintSelectionBlock(block: Rectangle2D) {
    val color = editor.editorModel.selectionModel.textAttributes.backgroundColor ?: return
    val layer = BackgroundLayer(
      color,
      HighlighterLayer.SELECTION,
      true,
      huggingHighlighting,
      false,
      Int.MAX_VALUE,
    )
    paintBackgroundBlock(block, layer)
  }

  /**
   * Adds one block with several [layers]. The list starts at the top layer.
   * The block records which of its styles lies directly above which, and [paint] keeps this order.
   */
  fun paintLayeredBlock(block: Rectangle2D, layers: List<BackgroundLayer>) {
    chain.clear()
    var lowest: BackgroundLayer? = null
    for (layer in layers) {
      if (layer.forcesBackground) {
        paintBackgroundBlock(block, layer)
        continue
      }
      if (lowest != null && layer > lowest) continue
      val styleId = styleIdOf(layer)
      addRow(rows, block, styleId)
      addToChain(styleId)
      lowest = layer
    }
  }

  fun paintBackgroundBlock(block: Rectangle2D, layer: BackgroundLayer) {
    addRow(rows, block, styleIdOf(layer))
  }

  /**
   * Adds the box of a background after the end of a line.
   * The box joins no other box and no style of [paint].
   */
  fun addAfterLineEndBox(block: Rectangle2D, layer: BackgroundLayer) {
    addRow(boxRows, block, boxStyles.size)
    boxStyles.add(layer)
  }

  /**
   * Adds the background of a line extension.
   * It covers each block that the painter added before it, where they overlap.
   * Each block that the painter adds after it covers it, where they overlap.
   */
  fun addLineExtensionBackground(block: Rectangle2D, color: Color) {
    addRow(extensionRows, block, extensionColors.size)
    extensionColors.add(color)
    rowCountsBeforeExtensions.add(rows.size)
  }

  /**
   * Fills the shapes of all styles from the bottom up.
   * A style fills after each style that a block of [paintLayeredBlock] puts below it.
   * The backgrounds of [addLineExtensionBackground] fill among the styles.
   * A background of a line extension fills after each style of an earlier block that overlaps it.
   * It fills before each style of a later block that overlaps it.
   * Of the ready ones, the first one fills next: the styles in the [BackgroundLayer] order, then the line extensions.
   * When the blocks put two of them in both orders, the first unpainted one of this order fills next.
   * Then the styles that force the background fill, in the [BackgroundLayer] order. They take no part in the order above.
   * A background of a line extension that no later block overlaps fills last.
   * A corner of a rounded shape gets the radius `lineHeight / 6`, limited to one half of each of its two edges.
   */
  fun paint(graphics: Graphics2D, lineHeight: Int) {
    if (styles.isEmpty() && extensionColors.isEmpty()) {
      return
    }
    val styleCount = styles.size
    fillShapes(graphics, lineHeight) { radius ->
      for (id in paintOrder()) {
        if (id < styleCount) {
          fillStyle(graphics, rows, id, styles[id], radius)
        } else {
          fillRow(graphics, extensionRows, id - styleCount, extensionColors[id - styleCount])
        }
      }
    }
  }

  /**
   * Fills the boxes of [addAfterLineEndBox] in the order of the calls.
   * Each box is a shape of its own. A rounded box gets the corners of [paint].
   */
  fun paintAfterLineEndBoxes(graphics: Graphics2D, lineHeight: Int) {
    if (boxStyles.isEmpty()) {
      return
    }
    fillShapes(graphics, lineHeight) { radius ->
      for (boxId in boxStyles.indices) {
        fillStyle(graphics, boxRows, boxId, boxStyles[boxId], radius)
      }
    }
  }

  /**
   * Runs [fill] with antialiasing in the coordinates of the rows.
   * It gives [fill] the corner radius of [paint].
   */
  private inline fun fillShapes(graphics: Graphics2D, lineHeight: Int, fill: (radius: Double) -> Unit) {
    val radius = cornerRadius(lineHeight)
    val config = GraphicsUtil.setupAAPainting(graphics)
    graphics.translate(0.0, yShift.toDouble())
    try {
      fill(radius)
    }
    finally {
      graphics.translate(0.0, -yShift.toDouble())
      config.restore()
    }
  }

  private fun addRow(target: BackgroundRows, block: Rectangle2D, styleId: Int) {
    val top = block.y - yShift
    target.add(
      styleId,
      top,
      top + block.height,
      block.x,
      block.x + block.width,
    )
  }

  /**
   * Puts [styleId] directly below the last style of the chain of the current block.
   * A style that the chain holds already lies higher in the block, so it keeps that place.
   */
  private fun addToChain(styleId: Int) {
    if (chain.contains(styleId)) {
      return
    }
    if (chain.isNotEmpty()) {
      edges.add(edgeOf(chain.getInt(chain.size - 1), styleId))
    }
    chain.add(styleId)
  }

  /**
   * Gives the ids of all styles and of all backgrounds of the line extensions in the order of [paint], from the bottom up.
   * An id below the number of styles is a style. A larger id is a background of a line extension, after the styles.
   */
  private fun paintOrder(): IntArray {
    val styleCount = styles.size
    val extensionCount = extensionColors.size
    val order = styles.indices.sortedBy { styles[it] }.toIntArray()
    if (edges.isEmpty() && extensionCount == 0) {
      return order
    }
    var layeredCount = styleCount
    while (layeredCount > 0 && styles[order[layeredCount - 1]].forcesBackground) {
      layeredCount--
    }
    val covered = addExtensionEdges()
    val candidates = order.copyOf(layeredCount + covered.count { it })
    var index = layeredCount
    for (extensionId in 0 until extensionCount) {
      if (covered[extensionId]) {
        candidates[index++] = styleCount + extensionId
      }
    }
    val result = sortByEdges(candidates, styleCount + extensionCount).copyOf(styleCount + extensionCount)
    order.copyInto(result, candidates.size, layeredCount, styleCount)
    index = candidates.size + styleCount - layeredCount
    for (extensionId in 0 until extensionCount) {
      if (!covered[extensionId]) {
        result[index++] = styleCount + extensionId
      }
    }
    return result
  }

  /**
   * Adds to [edges] the order of each background of a line extension.
   * The background lies above each style of an earlier row that overlaps it, and below each style of a later row that
   * overlaps it.
   * Gives for each background whether a later row overlaps it.
   * A row of a style that forces the background gives no edge, and it covers no background.
   */
  private fun addExtensionEdges(): BooleanArray {
    val styleCount = styles.size
    val covered = BooleanArray(extensionColors.size)
    for (extensionId in covered.indices) {
      val id = styleCount + extensionId
      val rowCountBefore = rowCountsBeforeExtensions.getInt(extensionId)
      for (index in 0 until rows.size) {
        val styleId = rows.styleId(index)
        if (styles[styleId].forcesBackground || !overlapsExtension(index, extensionId)) {
          continue
        }
        if (index < rowCountBefore) {
          edges.add(edgeOf(id, styleId))
        } else {
          edges.add(edgeOf(styleId, id))
          covered[extensionId] = true
        }
      }
    }
    return covered
  }

  /**
   * Says that the row [index] overlaps the background [extensionId] of a line extension by more than [MIN_OVERLAP] in
   * each direction.
   */
  private fun overlapsExtension(index: Int, extensionId: Int): Boolean {
    return rows.top(index) + MIN_OVERLAP < extensionRows.bottom(extensionId) &&
           extensionRows.top(extensionId) + MIN_OVERLAP < rows.bottom(index) &&
           rows.start(index) + MIN_OVERLAP < extensionRows.end(extensionId) &&
           extensionRows.start(extensionId) + MIN_OVERLAP < rows.end(index)
  }

  /**
   * Orders [candidates] so that each id comes after each id that [edges] put below it.
   * Among the ids that are ready, the first one of [candidates] comes next.
   * Each id is smaller than [idCount].
   */
  private fun sortByEdges(candidates: IntArray, idCount: Int): IntArray {
    val pairs = edges.toLongArray()
    val belowCounts = IntArray(idCount)
    for (edge in pairs) {
      belowCounts[aboveOf(edge)]++
    }
    val painted = BooleanArray(idCount)
    val result = IntArray(candidates.size)
    for (step in candidates.indices) {
      val next = nextStyle(candidates, candidates.size, painted, belowCounts)
      result[step] = next
      painted[next] = true
      for (edge in pairs) {
        if (belowOf(edge) == next) {
          belowCounts[aboveOf(edge)]--
        }
      }
    }
    return result
  }

  /**
   * Gives the first id of the first [count] ids of [order] that is not painted and has no unpainted id below it.
   * When every unpainted id has an unpainted id below it, the edges form a cycle.
   * Then the first unpainted id comes next.
   */
  private fun nextStyle(
    order: IntArray,
    count: Int,
    painted: BooleanArray,
    belowCounts: IntArray,
  ): Int {
    var first = -1
    for (index in 0 until count) {
      val styleId = order[index]
      if (painted[styleId]) {
        continue
      }
      if (belowCounts[styleId] == 0) {
        return styleId
      }
      if (first == -1) {
        first = styleId
      }
    }
    return first
  }

  private fun fillStyle(
    graphics: Graphics2D,
    source: BackgroundRows,
    styleId: Int,
    style: BackgroundLayer,
    radius: Double,
  ) {
    graphics.color = style.color
    if (style.isRounded) {
      graphics.fill(BackgroundOutline.build(source, styleId, radius))
      return
    }
    for (index in 0 until source.size) {
      if (source.styleId(index) != styleId) {
        continue
      }
      fillRow(graphics, source, index, style.color)
    }
  }

  /**
   * Fills the rectangle of the row [index] of [source] at the device pixels.
   */
  private fun fillRow(graphics: Graphics2D, source: BackgroundRows, index: Int, color: Color) {
    val x = source.start(index)
    val y = source.top(index)
    val rectangle = Rectangle2D.Double(x, y, source.end(index) - x, source.bottom(index) - y)
    EditorPainter.fillRectExact(graphics, rectangle, color)
  }

  /**
   * Gives the id of the style of [layer].
   * The style keeps the largest span of its layers.
   * The span orders the styles that no block of [paintLayeredBlock] orders.
   */
  private fun styleIdOf(layer: BackgroundLayer): Int {
    val known = styleIds[layer]
    if (known != null) {
      val style = styles[known]
      if (layer.span > style.span) {
        styles[known] = style.copy(span = layer.span)
      }
      return known
    }
    val id = styles.size
    styleIds[layer] = id
    styles.add(layer)
    return id
  }

  private fun edgeOf(above: Int, below: Int): Long {
    return (above.toLong() shl 32) or below.toLong()
  }

  private fun aboveOf(edge: Long): Int {
    return (edge ushr 32).toInt()
  }

  private fun belowOf(edge: Long): Int {
    return edge.toInt()
  }

  companion object {
    /**
     * Gives the radius of a corner of a rounded shape for [lineHeight].
     * A corner never gets a larger radius.
     */
    @JvmStatic
    fun cornerRadius(lineHeight: Int): Double {
      return lineHeight / 6.0
    }

    /**
     * Two rectangles that share a strip thinner than this do not overlap.
     */
    private const val MIN_OVERLAP: Double = 1e-2
  }
}
