// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleData
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.render.MinimapRenderEntry
import com.intellij.ide.minimap.render.MinimapTokenColorContext
import com.intellij.ide.minimap.scene.MinimapSceneBuilder
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import java.awt.Color
import java.awt.Font
import java.awt.geom.Rectangle2D

/**
 * Guards the token-color resolution used by the minimap token-filler layer.
 *
 * Each token entry resolves its color through [MinimapTokenColorContext], which looks up the
 * color run covering an offset. The lookup must stay correct at run boundaries and must not
 * degrade to an O(tokens²) scan on long, densely highlighted lines (a real EDT freeze, see
 * the binary search in [MinimapTokenColorContext]).
 */
class MinimapTokenColorContextTest : AbstractEditorTest() {

  private fun buildSnapshot(): MinimapSnapshot {
    val editor = editor
    val model = MinimapModel(editor)
    Disposer.register(testRootDisposable, model)
    val sceneBuilder = MinimapSceneBuilder(
      editor,
      model,
      MinimapLayoutCalculator(editor),
      MinimapGeometryCalculator(editor),
    )
    return WriteIntentReadAction.compute {
      sceneBuilder.buildSnapshot(
        panelWidth = 120,
        panelHeight = 600,
        scaleData = MinimapScaleData(width = 120, fitToHeight = false),
        // FILL (anything != FIT) selects EXACT layout, the per-token mode that this path serves.
        scaleMode = MinimapScaleMode.FILL,
      )
    }
  }

  private fun addForegroundRun(start: Int, end: Int, color: Color) {
    editor.markupModel.addRangeHighlighter(
      start,
      end,
      HighlighterLayer.ADDITIONAL_SYNTAX,
      TextAttributes(color, null, null, null, Font.PLAIN),
      HighlighterTargetArea.EXACT_RANGE,
    )
  }

  private fun colorContext(): MinimapTokenColorContext {
    val snapshot = buildSnapshot()
    return WriteIntentReadAction.compute {
      MinimapTokenColorContext(snapshot.context, snapshot.layoutMetrics)
    }
  }

  private fun colorAt(context: MinimapTokenColorContext, offset: Int): Color =
    WriteIntentReadAction.compute { context.colorFor(MinimapRenderEntry(null, DUMMY_RECT, sampleOffset = offset)) }

  fun testResolvesCorrectRunAtBoundaries() {
    // Three adjacent, distinctly colored runs on a single line.
    initText("aaaaabbbbbccccc")
    setEditorVisibleSize(80, 20)
    addForegroundRun(0, 5, Color.RED)
    addForegroundRun(5, 10, Color.GREEN)
    addForegroundRun(10, 15, Color.BLUE)

    val context = colorContext()

    val red = colorAt(context, 0)
    val green = colorAt(context, 5)
    val blue = colorAt(context, 10)

    assertTrue("distinctly colored runs must resolve to distinct minimap colors",
               red != green && green != blue && red != blue)

    // Every offset inside a run resolves to that run's color, including the last offset before
    // the boundary (the run is [start, end) — half-open).
    for (offset in 0 until 5) assertEquals("offset $offset belongs to the red run", red, colorAt(context, offset))
    for (offset in 5 until 10) assertEquals("offset $offset belongs to the green run", green, colorAt(context, offset))
    for (offset in 10 until 15) assertEquals("offset $offset belongs to the blue run", blue, colorAt(context, offset))
  }

  fun testResolvesEveryOffsetOnLongDenselyHighlightedLine() {
    // A single long line split into many small distinctly-colored runs. With the old linear
    // scan this resolution was O(runs²); it must stay correct (and fast) for every offset.
    val runCount = 256
    val runLength = 3
    val length = runCount * runLength
    initText("x".repeat(length))
    setEditorVisibleSize(80, 20)

    val expectedColors = ArrayList<Color>(runCount)
    for (i in 0 until runCount) {
      val color = Color((i * 37) % 256, (i * 91) % 256, (i * 53) % 256)
      expectedColors.add(color)
      addForegroundRun(i * runLength, (i + 1) * runLength, color)
    }

    val context = colorContext()

    // Reference color per run (post-softening); then assert every offset in the run matches it.
    for (i in 0 until runCount) {
      val runStart = i * runLength
      val reference = colorAt(context, runStart)
      for (offset in runStart until runStart + runLength) {
        assertEquals("offset $offset must resolve to run #$i", reference, colorAt(context, offset))
      }
    }
  }

  companion object {
    private val DUMMY_RECT = Rectangle2D.Double(0.0, 0.0, 1.0, 1.0)
  }
}
