// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.editor.repaintGutterForLine
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import kotlinx.coroutines.CoroutineScope
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent

/**
 * Draws and handles review controls in gutter
 */
internal class GitLabMergeRequestReviewControlsGutterRenderer(cs: CoroutineScope,
                                                              private val model: GitLabMergeRequestEditorReviewUIModel,
                                                              private val editor: EditorEx)
  : LineMarkerRenderer, LineMarkerRendererEx, ActiveGutterRenderer, Disposable {

  private var hoveredLineInRangeIdx: Int = -1
  private var iconHovered: Boolean = false

  init {
    cs.launchNow {
      model.commentableRanges.collect {
        onRangesChanged()
      }
    }
  }

  private fun onRangesChanged() {
    hoveredLineInRangeIdx = -1
    iconHovered = false

    val xRange = getIconColumnXRange(editor)
    with(editor.gutterComponentEx) {
      repaint(xRange.first, 0, xRange.last - xRange.first, height)
    }
  }

  private val mouseListener = object : EditorMouseListener, EditorMouseMotionListener {
    override fun mouseMoved(e: EditorMouseEvent) {
      editor.repaintGutterForLine(hoveredLineInRangeIdx)
      val line = e.logicalPosition.line
      if (model.commentableRanges.value.any { line in it.start2 until it.end2 }) {
        hoveredLineInRangeIdx = line
        iconHovered = isIconColumnHovered(editor, e.mouseEvent)
        editor.repaintGutterForLine(e.logicalPosition.line)
      }
      else {
        // no need to re-calc column
        hoveredLineInRangeIdx = -1
      }
    }

    override fun mouseExited(e: EditorMouseEvent) {
      editor.repaintGutterForLine(hoveredLineInRangeIdx)
      hoveredLineInRangeIdx = -1
      iconHovered = false
    }
  }

  init {
    editor.gutterComponentEx.reserveLeftFreePaintersAreaWidth(this, WIDTH)
    editor.addEditorMouseListener(mouseListener)
    editor.addEditorMouseMotionListener(mouseListener)
  }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val hoveredLineIdx = hoveredLineInRangeIdx
    val iconHovered = iconHovered
    // do not paint invalid range
    if (hoveredLineIdx < 0 || hoveredLineIdx > editor.document.lineCount) return
    val yRange = EditorUtil.logicalLineToYRange(editor, hoveredLineIdx).second ?: return

    val icon = if (iconHovered) AllIcons.General.InlineAddHover else AllIcons.General.InlineAdd
    val intervalCenter = yRange.intervalStart() + (yRange.intervalEnd() - yRange.intervalStart()) / 2
    val y = intervalCenter - icon.iconWidth / 2
    icon.paintIcon(null, g, r.x, y)
  }

  override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
    val hoveredLineIdx = hoveredLineInRangeIdx
    val iconHovered = iconHovered
    // do not paint invalid range
    if (hoveredLineIdx < 0 || hoveredLineIdx > editor.document.lineCount || !iconHovered) return false
    val yRange = EditorUtil.logicalLineToYRange(editor, hoveredLineIdx).second ?: return false

    val icon = AllIcons.General.InlineAddHover
    if (yRange.intervalEnd() - yRange.intervalStart() <= icon.iconWidth) return true
    val intervalCenter = yRange.intervalStart() + (yRange.intervalEnd() - yRange.intervalStart()) / 2
    val iconStartY = intervalCenter - icon.iconWidth / 2
    val iconEndY = intervalCenter + icon.iconWidth / 2
    return e.y in iconStartY until iconEndY
  }

  override fun doAction(editor: Editor, e: MouseEvent) {
    val hoveredLineIdx = hoveredLineInRangeIdx
    if (hoveredLineIdx < 0 || !iconHovered) return
    model.requestNewDiscussion(hoveredLineIdx, true)
    e.consume()
  }

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.LEFT

  override fun dispose() {
    editor.removeEditorMouseListener(mouseListener)
    editor.removeEditorMouseMotionListener(mouseListener)
  }

  companion object {
    private const val WIDTH = 16

    private fun isIconColumnHovered(editor: EditorEx, e: MouseEvent): Boolean {
      if (e.component !== editor.gutter) return false
      val x = convertX(editor, e.x)
      return x in getIconColumnXRange(editor)
    }

    private fun getIconColumnXRange(editor: EditorEx): IntRange {
      val iconStart = editor.gutterComponentEx.lineMarkerAreaOffset
      val iconEnd = iconStart + WIDTH
      return iconStart until iconEnd
    }

    private fun convertX(editor: EditorEx, x: Int): Int {
      if (editor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT) return x
      return editor.gutterComponentEx.width - x
    }
  }
}