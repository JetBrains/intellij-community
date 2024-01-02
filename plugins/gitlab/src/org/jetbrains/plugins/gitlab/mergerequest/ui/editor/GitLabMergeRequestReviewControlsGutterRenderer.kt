// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.collaboration.async.launchNow
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.util.Disposer
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import kotlin.math.min
import kotlin.properties.Delegates.observable

/**
 * Draws and handles review controls in gutter
 */
internal class GitLabMergeRequestReviewControlsGutterRenderer
private constructor(cs: CoroutineScope,
                    nonCommentableRanges: StateFlow<List<LineRange>>,
                    private val editor: EditorEx,
                    private val requestNewDiscussion: (lineIdx: Int) -> Unit)
  : LineMarkerRenderer, LineMarkerRendererEx, ActiveGutterRenderer {

  private val hoverHandler = HoverHandler(editor)

  init {
    cs.launchNow {
      val areaDisposable = Disposer.newDisposable()
      editor.gutterComponentEx.reserveLeftFreePaintersAreaWidth(areaDisposable, ICON_AREA_WIDTH)
      editor.addEditorMouseListener(hoverHandler)
      editor.addEditorMouseMotionListener(hoverHandler)

      try {
        nonCommentableRanges.collect {
          hoverHandler.nonCommentableRanges = it
        }
      }
      finally {
        editor.removeEditorMouseListener(hoverHandler)
        editor.removeEditorMouseMotionListener(hoverHandler)
        Disposer.dispose(areaDisposable)
      }
    }
  }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    paintCommentIcons(editor, g, r)
    paintNewCommentIcon(editor, g, r)
  }

  /**
   * Paint comment icons on each line containing discussion renderers
   */
  private fun paintCommentIcons(editor: Editor, g: Graphics, r: Rectangle) {
    editor.inlayModel
      .getBlockElementsInRange(0, editor.document.textLength, GitLabMergeRequestDiscussionInlayRenderer::class.java)
      .map { editor.document.getLineNumber(it.offset) }.forEach { lineIdx ->
        val yRange = EditorUtil.logicalLineToYRange(editor, lineIdx).first
        val lineCenter = yRange.intervalStart() + editor.lineHeight / 2
        val icon = CollaborationToolsIcons.Comment
        val y = lineCenter - icon.iconWidth / 2
        icon.paintIcon(null, g, r.x, y)
      }
  }

  /**
   * Paint a new comment icon on hovered line if line is not folded and if there's enough vertical space
   */
  private fun paintNewCommentIcon(editor: Editor, g: Graphics, r: Rectangle) {
    val lineData = hoverHandler.calcHoveredLineData() ?: return
    if (!lineData.commentable) return

    val yShift = if (lineData.hasComments) editor.lineHeight else 0
    // do not paint if there's not enough space
    if (yShift > 0 && lineData.yRangeWithInlays.last - lineData.yRangeWithInlays.first < yShift + editor.lineHeight) return

    val icon = if (lineData.columnHovered) AllIcons.General.InlineAddHover else AllIcons.General.InlineAdd
    val y = lineData.yRangeWithInlays.first + yShift + (editor.lineHeight - icon.iconHeight) / 2
    icon.paintIcon(null, g, r.x, y)
  }

  override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
    val lineData = hoverHandler.calcHoveredLineData() ?: return false
    if (!lineData.columnHovered) return false
    if (!lineData.hasComments && !lineData.commentable) return false

    var actionableHeight = 0
    if (lineData.hasComments) actionableHeight += editor.lineHeight
    if (lineData.commentable) actionableHeight += editor.lineHeight

    val yRange = lineData.yRangeWithInlays
    val actionableYStart = yRange.first
    val actionableYEnd = min(yRange.first + actionableHeight, yRange.last)
    return e.y in actionableYStart..actionableYEnd
  }

  override fun doAction(editor: Editor, e: MouseEvent) {
    val lineData = hoverHandler.calcHoveredLineData() ?: return
    if (!lineData.columnHovered) return
    when {
      lineData.hasComments && lineData.commentable -> {
        val hoveredIconIdx = getHoveredIconSlotIndex(lineData.yRangeWithInlays, e.y)
        when (hoveredIconIdx) {
          0 -> unfoldOrToggle(lineData)
          1 -> unfoldOrRequestNewDiscussion(lineData)
          else -> return
        }
      }
      lineData.hasComments -> unfoldOrToggle(lineData)
      lineData.commentable -> unfoldOrRequestNewDiscussion(lineData)
      else -> return
    }
    e.consume()
  }

  private fun getHoveredIconSlotIndex(range: IntRange, y: Int): Int {
    if (y < range.first) return -1
    var idx: Int = -1
    for (slotEnd in range.first + editor.lineHeight..range.last step editor.lineHeight) {
      idx++
      if (y < slotEnd) {
        break
      }
    }
    return idx
  }

  private fun unfoldOrRequestNewDiscussion(lineData: LogicalLineData) {
    val foldedRegion = lineData.foldedRegion
    if (foldedRegion != null) foldedRegion.unfold() else requestNewDiscussion(lineData.logicalLine)
  }

  private fun unfoldOrToggle(lineData: LogicalLineData) {
    val foldedRegion = lineData.foldedRegion
    if (foldedRegion != null) foldedRegion.unfold() else toggleDiscussions(lineData.discussionRenderers)
  }

  private fun FoldRegion.unfold() {
    if (this is CustomFoldRegion) {
      val renderer = renderer
      if (renderer is DocRenderer) {
        renderer.item.toggle()
        return
      }
    }
    else {
      editor.foldingModel.runBatchFoldingOperation {
        isExpanded = true
      }
    }
  }

  private fun toggleDiscussions(renderers: List<GitLabMergeRequestDiscussionInlayRenderer>) {
    // probably better to move this to VM, but it's tricky
    val hideAll = renderers.any { it.isVisible }
    renderers.forEach {
      it.isVisible = !hideAll
    }
  }

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.LEFT

  companion object {
    private const val ICON_AREA_WIDTH = 16

    private class LogicalLineData(
      editor: EditorEx, nonCommentableRanges: List<LineRange>, val logicalLine: Int, val columnHovered: Boolean
    ) {
      private val lineStartOffset = editor.document.getLineStartOffset(logicalLine)
      private val lineEndOffset = editor.document.getLineEndOffset(logicalLine)

      private val yRange by lazy {
        val startVisualLine = editor.offsetToVisualLine(lineStartOffset, false)
        val softWrapCount = editor.getSoftWrapModel().getSoftWrapsForRange(lineStartOffset + 1, lineEndOffset - 1).size
        val endVisualLine = startVisualLine + softWrapCount
        val startY = editor.visualLineToY(startVisualLine)
        val endY = (if (endVisualLine == startVisualLine) startY else editor.visualLineToY(endVisualLine)) + editor.getLineHeight()
        startY..endY
      }

      val foldedRegion: FoldRegion? by lazy {
        editor.foldingModel.getCollapsedRegionAtOffset(lineEndOffset)
      }

      val yRangeWithInlays: IntRange by lazy {
        val visualLine = editor.offsetToVisualLine(lineEndOffset, false)
        var inlaysBelowHeight = 0
        editor.inlayModel.getBlockElementsForVisualLine(visualLine, false).forEach {
          inlaysBelowHeight += it.heightInPixels
        }
        yRange.first..yRange.last + inlaysBelowHeight.coerceAtLeast(0)
      }

      val discussionRenderers: List<GitLabMergeRequestDiscussionInlayRenderer> by lazy {
        val rangeEnd = foldedRegion?.endOffset ?: lineEndOffset
        editor.inlayModel.getBlockElementsInRange(lineStartOffset, rangeEnd).mapNotNull {
          it.renderer as? GitLabMergeRequestDiscussionInlayRenderer
        }
      }

      val hasComments: Boolean by lazy(discussionRenderers::isNotEmpty)

      val commentable: Boolean by lazy {
        val inCommentableRange = nonCommentableRanges.none { logicalLine in it.start until it.end }
        inCommentableRange
      }
    }

    /**
     * Handles the hover state of the rendered icons
     * Use [calcHoveredLineData] to acquire a current state
     */
    private class HoverHandler(private val editor: EditorEx) : EditorMouseListener, EditorMouseMotionListener {
      private var hoveredLogicalLine: Int? = null
      private var columnHovered: Boolean = false

      var nonCommentableRanges: List<LineRange> by observable(emptyList()) { _, _, _ ->
        hoveredLogicalLine = null
        columnHovered = false
        repaintColumn()
      }

      fun calcHoveredLineData(): LogicalLineData? {
        val logicalLine = hoveredLogicalLine?.takeIf { it in 0 until editor.document.lineCount } ?: return null
        return LogicalLineData(editor, nonCommentableRanges, logicalLine, columnHovered)
      }

      override fun mouseMoved(e: EditorMouseEvent) {
        val line = e.logicalPosition.line.coerceAtLeast(0)
        val prevLine = if (line != hoveredLogicalLine) hoveredLogicalLine else null
        if (line in 0 until DiffUtil.getLineCount(editor.document)) {
          hoveredLogicalLine = line
        }
        else {
          hoveredLogicalLine = null
        }
        columnHovered = isIconColumnHovered(editor, e.mouseEvent)
        if (prevLine != null) {
          repaintColumn(prevLine)
        }
        repaintColumn(e.logicalPosition.line)
      }

      override fun mouseExited(e: EditorMouseEvent) {
        repaintColumn(hoveredLogicalLine)
        hoveredLogicalLine = null
        columnHovered = false
      }

      private fun repaintColumn(line: Int? = null) {
        val xRange = getIconColumnXRange(editor)
        val yRange = if (line != null && line > 0) {
          val y = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
          y..y + editor.lineHeight
        }
        else {
          0..editor.gutterComponentEx.height
        }
        editor.gutterComponentEx.repaint(xRange.first, yRange.first, xRange.last - xRange.first, yRange.last)
      }

      private fun isIconColumnHovered(editor: EditorEx, e: MouseEvent): Boolean {
        if (e.component !== editor.gutter) return false
        val x = convertX(editor, e.x)
        return x in getIconColumnXRange(editor)
      }

      private fun getIconColumnXRange(editor: EditorEx): IntRange {
        val iconStart = editor.gutterComponentEx.lineMarkerAreaOffset
        val iconEnd = iconStart + ICON_AREA_WIDTH
        return iconStart until iconEnd
      }

      private fun convertX(editor: EditorEx, x: Int): Int {
        if (editor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT) return x
        return editor.gutterComponentEx.width - x
      }
    }

    fun setupIn(cs: CoroutineScope,
                nonCommentableRanges: StateFlow<List<LineRange>>,
                editor: EditorEx,
                requestNewDiscussion: (lineIdx: Int) -> Unit) {
      val renderer = GitLabMergeRequestReviewControlsGutterRenderer(cs, nonCommentableRanges, editor, requestNewDiscussion)
      val highlighter = editor.markupModel.addRangeHighlighter(null, 0, editor.document.textLength,
                                                               DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                               HighlighterTargetArea.LINES_IN_RANGE).apply {
        setGreedyToLeft(true)
        setGreedyToRight(true)
        setLineMarkerRenderer(renderer)
      }
      cs.launchNow {
        try {
          awaitCancellation()
        }
        finally {
          highlighter.dispose()
        }
      }
    }
  }
}