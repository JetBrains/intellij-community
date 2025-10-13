// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.action.findFocusedThreadId
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.flatMapLatestEach
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorInlaysModel
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.ui.FocusUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.ui.comment.CommentedCodeFrameRenderer
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPREditorMappedComponentModel
import java.awt.*
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.plaf.LayerUI
import kotlin.math.abs

internal object GHPRInlayUtils {
  internal fun installInlayHoverOutline(
    parentCs: CoroutineScope,
    editor: Editor,
    side: Side?,
    locationToLine: ((DiffLineLocation) -> Int?)?,
    vm: GHPREditorMappedComponentModel,
  ) {
    val cs: CoroutineScope = parentCs.childScope("Comment inlay hover controller")
    var activeLineHighlighter: RangeHighlighter? = null
    val frameResizer = if (vm is GHPREditorMappedComponentModel.NewComment<*>) {
      val frameResizer = ResizingFrameListener(editor, vm)
      editor.addEditorMouseMotionListener(frameResizer)
      editor.addEditorMouseListener(frameResizer)
      frameResizer
    }
    else null

    cs.launchNow {
      vm.shouldShowOutline.combineState(vm.range, ::Pair).collectLatest { (shouldShowOutline, range) ->
        activeLineHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        activeLineHighlighter = null
        if (!shouldShowOutline) {
          return@collectLatest
        }
        val inlayCommentRange = range
        if (inlayCommentRange == null) return@collectLatest
        val commentRange = if (locationToLine == null) {
          inlayCommentRange.second
        }
        else {
          val startLine = locationToLine(inlayCommentRange.first to inlayCommentRange.second.first) ?: return@collectLatest
          val endLine = locationToLine(inlayCommentRange.first to inlayCommentRange.second.last) ?: return@collectLatest
          startLine..endLine
        }
        val startOffset = editor.document.getLineStartOffset(commentRange.first)
        val endOffset = editor.document.getLineEndOffset(commentRange.last)
        val renderer = CommentedCodeFrameRenderer(commentRange.first, commentRange.last, side)
        activeLineHighlighter = editor.markupModel.addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE).also { highlighter ->
          highlighter.customRenderer = renderer
          highlighter.lineMarkerRenderer = renderer
        }
      }
    }
    cs.launch {
      try {
        awaitCancellation()
      }
      finally {
        if (frameResizer != null) {
          val editorEx = editor as EditorEx
          editorEx.setCustomCursor(frameResizer, null)
          editor.removeEditorMouseListener(frameResizer)
          editor.removeEditorMouseMotionListener(frameResizer)
        }
        activeLineHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        activeLineHighlighter = null
      }
    }
  }

  private class ResizingFrameListener(val editor: Editor, val vm: GHPREditorMappedComponentModel.NewComment<*>) : EditorMouseListener, EditorMouseMotionListener {
    private val editorEx = editor as EditorEx
    private val model = editorEx.getUserData(CodeReviewCommentableEditorModel.KEY) as? CodeReviewEditorGutterControlsModel.WithMultilineComments

    private var isDraggingFrame: Boolean = false
    private var dragStart: Int = 0
    private var edge: Edge? = Edge.TOP
    private var oldRange: LineRange? = null

    private val resizeCursor: Cursor = try {
      Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
    }
    catch (_: IllegalArgumentException) {
      Cursor.getDefaultCursor()
    }

    override fun mouseDragged(e: EditorMouseEvent) {
      if (!isDraggingFrame || edge == null || oldRange == null) return
      e.consume() // to prevent selecting text while dragging
      val mouseY = e.mouseEvent.y.toFloat()
      val dragDelta = mouseY - dragStart
      val direction = if (dragDelta > 0) 1 else -1

      val startVisual = editor.yToVisualLine(dragStart)
      val currentVisual = editor.yToVisualLine(mouseY.toInt())
      val stepCount = abs(currentVisual - startVisual)

      val range = vm.range.value ?: return
      var newStart = range.second.first
      var newEnd = range.second.last

      when (edge) {
        Edge.TOP -> {
          val start = oldRange?.start ?: return
          newStart = (start + direction * stepCount)
            .coerceIn(0, newEnd)
        }
        Edge.BOTTOM -> {
          val end = oldRange?.end ?: return
          newEnd = (end + direction * stepCount)
            .coerceIn(newStart, editor.document.lineCount - 1)
        }
        else -> Unit
      }
      val newRange = newStart..newEnd
      if (model?.canCreateComment(LineRange(newStart, newEnd)) ?: false) {
        vm.setRange(range.copy(second = newRange))
      }
    }

    override fun mouseReleased(e: EditorMouseEvent) {
      isDraggingFrame = false
      if (oldRange != null && model != null) {
        val range = vm.range.value?.second ?: return
        model.updateCommentLines(oldRange!!, LineRange(range.first, range.last))
        vm.isHidden(false)
        editorEx.setCustomCursor(this, null)
      }
    }

    override fun mousePressed(event: EditorMouseEvent) {
      val point = event.mouseEvent.point ?: return
      val yBordersPositions = getYAxisBorders() ?: return
      edge = point.getEdge(yBordersPositions) ?: return
      val range = vm.range.value?.second ?: return
      oldRange = LineRange(range.first, range.last)
      dragStart = when (edge) {
        Edge.TOP -> yBordersPositions.first
        Edge.BOTTOM -> yBordersPositions.second - 1
        else -> return
      }.toInt()
      isDraggingFrame = true
      vm.isHidden(true)
    }

    override fun mouseMoved(e: EditorMouseEvent) {
      val yBorders = getYAxisBorders() ?: return
      val onEdge = e.mouseEvent.point.getEdge(yBorders) != null
      val gutterGlassComp = IdeGlassPaneUtil.find(editorEx.gutterComponentEx)
      if (onEdge) {
        editorEx.setCustomCursor(this, resizeCursor)
        gutterGlassComp.setCursor(resizeCursor, this)
      }
      else {
        editorEx.setCustomCursor(this, null)
        gutterGlassComp.setCursor(null, this)
      }
    }

    private fun Point.getEdge(frameCoords: Pair<Float, Float>): Edge? {
      val topY = frameCoords.first
      val botY = frameCoords.second
      if (this.x.toFloat() !in 0f..editor.contentComponent.width.toFloat()) return null
      if (this.y.toFloat() in (topY - 3).coerceAtLeast(0f)..topY + editor.lineHeight / 2) return Edge.TOP
      if (this.y.toFloat() in botY - editor.lineHeight / 2..botY + 3) return Edge.BOTTOM
      return null
    }

    private fun getYAxisBorders(): Pair<Float, Float>? {
      val range = vm.range.value?.second ?: return null
      val doc = editor.document
      val topOffset = doc.getLineStartOffset(range.first)
      val bottomOffset = doc.getLineEndOffset(range.last)
      val topY = editor.visualLineToY(editor.offsetToVisualPosition(topOffset).line).toFloat()
      val bottomY = editor.visualLineToY(editor.offsetToVisualPosition(bottomOffset).line).toFloat() + editor.lineHeight
      return topY to bottomY
    }

    private enum class Edge {
      TOP, BOTTOM
    }
  }

  internal fun installInlaysFocusTracker(cs: CoroutineScope, model: CodeReviewEditorInlaysModel<*>, project: Project) {
    val focusedThreadFlow: Flow<String?> = callbackFlow {
      val focusListener = PropertyChangeListener { evt ->
        if (evt.propertyName == "focusOwner") {
          trySend(findFocusedThreadId(project))
        }
      }

      FocusUtil.addFocusOwnerListener(cs.asDisposable(), focusListener)
      send(findFocusedThreadId(project))
      awaitClose()
    }
    cs.launch {
      model.inlays
        .map { it.filterIsInstance<GHPREditorMappedComponentModel>() }
        .combine(focusedThreadFlow) { inlays, focusedThreadId ->
          inlays.forEach { it.setFocused(it.key == focusedThreadId) }
        }.collect()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun installInlaysDimming(cs: CoroutineScope, model: CodeReviewEditorInlaysModel<*>, locationToLine: ((DiffLineLocation) -> Int?)?) {
    cs.launchNow {
      model.inlays
        .map { it.filterIsInstance<GHPREditorMappedComponentModel>() }
        .flatMapLatestEach { item ->
          combine(item.shouldShowOutline, item.range) { shouldShowOutline, range ->
            InlayState(item, shouldShowOutline, range)
          }
        }
        .collectLatest { inlayStates ->
          val rangesToDim = inlayStates
            .filter { it.shouldShowOutline }
            .mapNotNull {
              val (side, lines) = it.range ?: return@mapNotNull null
              if (locationToLine != null) {
                val startLine = locationToLine(side to lines.first) ?: return@mapNotNull null
                val endLine = locationToLine(side to lines.last) ?: return@mapNotNull null
                startLine..<endLine
              }
              else {
                lines.first..<lines.last
              }
            }

          inlayStates.forEach { (vm, _, range) ->
            val (side, lines) = range ?: return@forEach
            val onLine = if (locationToLine != null) {
              locationToLine(side to lines.last) ?: return@forEach
            }
            else {
              lines.last
            }

            vm.setDimmed(rangesToDim.any { dimRange -> dimRange.contains(onLine) })
          }
        }
    }
  }

  private data class InlayState(
    val inlay: GHPREditorMappedComponentModel,
    val shouldShowOutline: Boolean,
    val range: Pair<Side, IntRange>?,
  )
}

internal class FadeLayerUI : LayerUI<JComponent>() {
  private var alpha: Float = 1f

  fun setAlpha(a: Float, jLayer: JLayer<JComponent>) {
    alpha = a.coerceIn(0f, 1f)
    jLayer.repaint()
  }

  override fun paint(g: Graphics, c: JComponent) {
    val g2 = g.create() as Graphics2D
    try {
      val old = g2.composite
      g2.composite = AlphaComposite.SrcOver.derive(alpha)
      super.paint(g2, c)
      g2.composite = old
    }
    finally {
      g2.dispose()
    }
  }
}