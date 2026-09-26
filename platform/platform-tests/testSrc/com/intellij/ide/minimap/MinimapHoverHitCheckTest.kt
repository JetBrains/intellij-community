// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapGeometryData
import com.intellij.ide.minimap.hover.MinimapHoverHitCheck
import com.intellij.ide.minimap.hover.MinimapHoverHitCheckResult
import com.intellij.ide.minimap.layout.MinimapLayoutMode
import com.intellij.ide.minimap.model.MinimapLineProjection
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.render.MinimapRenderEntry
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.util.TextRange
import com.intellij.util.ui.EmptyIcon
import java.awt.Point
import java.awt.geom.Rectangle2D
import javax.swing.Icon

/**
 * Guards [MinimapHoverHitCheck.resolveHit]: geometry-first hit detection (smallest matching entry wins) followed
 * by resolving the presentation of the winning entry only. The presentation call is intentionally kept out of the
 * per-entry loop and out of the EDT path (see [com.intellij.ide.minimap.hover.MinimapHoverController]) so that a
 * language's lazy resolve update does not try to run a modal progress while a read lock is held.
 */
class MinimapHoverHitCheckTest : AbstractEditorTest() {

  private fun context(): MinimapRenderContext {
    val editor = editor
    return MinimapRenderContext(
      editor = editor,
      panelWidth = 120,
      panelHeight = 600,
      geometry = MinimapGeometryData(minimapHeight = 600, areaStart = 0, areaEnd = 600, thumbStart = 0, thumbHeight = 100),
      lineProjection = MinimapLineProjection.identity(editor.document.lineCount),
    )
  }

  private fun lineEntry(line: Int, text: String): Pair<MinimapRenderEntry, RecordingStructureElement> {
    val document = editor.document
    val range = TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
    val element = RecordingStructureElement(range, text)
    return MinimapRenderEntry(element = element, rect2d = Rectangle2D.Double()) to element
  }

  /** [MinimapHoverHitCheck.resolveHit] is `@RequiresBackgroundThread @RequiresReadLock`, so call it as production does. */
  private fun resolveHitOffEdt(hitChecker: MinimapHoverHitCheck, snapshot: MinimapSnapshot, point: Point): MinimapHoverHitCheckResult? {
    return ApplicationManager.getApplication()
      .executeOnPooledThread<MinimapHoverHitCheckResult?> {
        ReadAction.computeBlocking<MinimapHoverHitCheckResult?, RuntimeException> { hitChecker.resolveHit(snapshot, point) }
      }
      .get()
  }

  private fun snapshotOf(context: MinimapRenderContext, vararg entries: MinimapRenderEntry): MinimapSnapshot {
    return MinimapSnapshot(
      context = context,
      geometry = context.geometry,
      tokenEntries = emptyList(),
      structureEntries = entries.toList(),
      diagnosticEntries = emptyList(),
      breakpointEntries = emptyList(),
      foldEntries = emptyList(),
      layoutMetrics = null,
      layoutMode = MinimapLayoutMode.EXACT,
    )
  }

  fun testResolvesPresentationOfEntryUnderPoint() {
    initText((0 until 20).joinToString("\n") { "line$it" })
    val context = context()
    val hitChecker = MinimapHoverHitCheck(editor)

    val (entry, element) = lineEntry(line = 5, text = "fn five")
    val snapshot = snapshotOf(context, entry)

    val rect = hitChecker.computeHoverRect(entry, context)!!
    val point = Point(rect.centerX.toInt(), rect.centerY.toInt())

    val result = resolveHitOffEdt(hitChecker, snapshot, point)

    assertNotNull("the entry under the point should produce a hover target", result)
    assertEquals("fn five", result!!.text)
    assertSame(EmptyIcon.ICON_16, result.icon)
    assertTrue("presentation must be resolved for the winning entry", element.presentationResolved)
  }

  fun testSmallestMatchingEntryWins() {
    initText((0 until 20).joinToString("\n") { "line$it" })
    val context = context()
    val hitChecker = MinimapHoverHitCheck(editor)

    // A wide entry covering many lines and a narrow single-line entry that both contain the point.
    val (wideEntry, wideElement) = run {
      val document = editor.document
      val range = TextRange(document.getLineStartOffset(0), document.getLineEndOffset(15))
      val element = RecordingStructureElement(range, "outer")
      MinimapRenderEntry(element = element, rect2d = Rectangle2D.Double()) to element
    }
    val (narrowEntry, narrowElement) = lineEntry(line = 5, text = "inner")
    val snapshot = snapshotOf(context, wideEntry, narrowEntry)

    val narrowRect = hitChecker.computeHoverRect(narrowEntry, context)!!
    val point = Point(narrowRect.centerX.toInt(), narrowRect.centerY.toInt())

    val result = resolveHitOffEdt(hitChecker, snapshot, point)

    assertNotNull(result)
    assertEquals("the smallest entry containing the point should win", "inner", result!!.text)
    // Geometry-first: only the winning entry's presentation is resolved, not every entry's.
    assertTrue(narrowElement.presentationResolved)
    assertFalse("the losing entry's presentation must not be resolved", wideElement.presentationResolved)
  }

  fun testNoTargetWhenPointMissesEntries() {
    initText((0 until 20).joinToString("\n") { "line$it" })
    val context = context()
    val hitChecker = MinimapHoverHitCheck(editor)

    val (entry, _) = lineEntry(line = 5, text = "fn five")
    val snapshot = snapshotOf(context, entry)

    val result = resolveHitOffEdt(hitChecker, snapshot, Point(0, context.geometry.minimapHeight + 5_000))

    assertNull("a point outside every entry rect must not produce a hover target", result)
  }

  private class RecordingStructureElement(private val range: TextRange, private val text: String) : StructureViewTreeElement {
    var presentationResolved: Boolean = false
      private set

    override fun getValue(): Any = range

    override fun getPresentation(): ItemPresentation {
      presentationResolved = true
      return object : ItemPresentation {
        override fun getPresentableText(): String = text
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon = EmptyIcon.ICON_16
      }
    }

    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY
  }
}
