// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.Point
import kotlin.math.min

/**
 * The text that the editor component exposes to assistive technologies, and the conversions between its offsets and document offsets.
 * When screen reader support is not enabled, this text matches the document text.
 * When screen reader support is enabled, this text matches what the editor shows, with placeholders for collapsed fold regions instead of their document text.
 * Otherwise, a screen reader would read folded code that the caret skips, and its lines would not match the editor lines.
 */
internal class EditorAccessibleText(private val editor: EditorImpl) {
  private class Fold(val start: Int, val end: Int, val placeholder: String, val visibleStart: Int) {
    val visibleEnd: Int get() = visibleStart + placeholder.length
  }

  /** The folding model makes a new array of collapsed regions after each document or folding change, so the array identifies the folds. */
  private class Snapshot(val regions: Array<FoldRegion>, val folds: Array<Fold>)

  @Volatile
  private var snapshot: Snapshot? = null

  private val document get() = editor.document

  fun getLength(): Int {
    val last = folds().lastOrNull() ?: return document.textLength
    return document.textLength + last.visibleEnd - last.end
  }

  fun getText(start: Int, end: Int): String {
    val folds = folds()
    val chars = document.charsSequence
    if (folds.isEmpty()) return chars.subSequence(start, end).toString()

    val result = StringBuilder(end - start)
    var i = folds.lastIndexAtOrBefore(start) { it.visibleStart }
    var offset = start
    while (offset < end) {
      val fold = folds.getOrNull(i)
      if (fold != null && offset < fold.visibleEnd) {
        val chunkEnd = min(end, fold.visibleEnd)
        result.append(fold.placeholder, offset - fold.visibleStart, chunkEnd - fold.visibleStart)
        offset = chunkEnd
      }
      else {
        // the document text between this fold and the next one
        val chunkEnd = min(end, folds.getOrNull(i + 1)?.visibleStart ?: end)
        val shift = if (fold == null) 0 else fold.visibleEnd - fold.end
        result.append(chars, offset - shift, chunkEnd - shift)
        offset = chunkEnd
        i++
      }
    }
    return result.toString()
  }

  fun getLineCount(): Int {
    if (folds().isEmpty()) return document.lineCount
    return document.lineCount - editor.foldingModel.totalNumberOfFoldedLines
  }

  fun getLineNumber(offset: Int): Int {
    val folds = folds()
    if (folds.isEmpty()) return document.getLineNumber(offset)
    val documentOffset = toDocumentOffset(folds, offset, false)
    return document.getLineNumber(documentOffset) - editor.foldingModel.getFoldedLinesCountBefore(documentOffset)
  }

  fun getLineStartOffset(line: Int): Int {
    val folds = folds()
    if (folds.isEmpty()) return document.getLineStartOffset(line)
    val documentLine = editor.foldingModel.getLogicalLineForVisualLineWithoutSoftWraps(line)
    return fromDocumentOffset(folds, document.getLineStartOffset(documentLine))
  }

  fun getLineEndOffset(line: Int): Int {
    if (folds().isEmpty()) return document.getLineEndOffset(line)
    // Excludes the line separator, as com.intellij.openapi.editor.Document.getLineEndOffset does.
    return if (line + 1 < getLineCount()) getLineStartOffset(line + 1) - 1 else getLength()
  }

  fun toDocumentOffset(offset: Int): Int = toDocumentOffset(folds(), offset, false)

  fun getPlaceholderRange(offset: Int): TextRange? {
    val folds = folds()
    val fold = folds.getOrNull(folds.lastIndexAtOrBefore(offset) { it.visibleStart }) ?: return null
    return if (offset < fold.visibleEnd) TextRange(fold.visibleStart, fold.visibleEnd) else null
  }

  /**
   * A range that ends in a placeholder includes all of its fold region.
   * An empty range stays empty, so that a caret in a placeholder does not become an edit of the folded text.
   */
  fun toDocumentRange(start: Int, end: Int): TextRange {
    val folds = folds()
    val documentStart = toDocumentOffset(folds, start, false)
    return UnfairTextRange(documentStart, if (start == end) documentStart else toDocumentOffset(folds, end, true))
  }

  /**
   * The start of a collapsed fold region maps to the start of its placeholder.
   * An offset in the region maps to the end of the placeholder.
   */
  fun fromDocumentOffset(documentOffset: Int): Int = fromDocumentOffset(folds(), documentOffset)

  fun getPlaceholderVisualPosition(offset: Int): VisualPosition? {
    val folds = folds()
    val fold = folds.getOrNull(folds.lastIndexAtOrBefore(offset) { it.visibleStart }) ?: return null
    if (offset == fold.visibleStart || offset >= fold.visibleEnd) return null
    val foldStart = editor.offsetToVisualPosition(fold.start)
    return VisualPosition(foldStart.line, foldStart.column + offset - fold.visibleStart)
  }

  fun getCaretOffset(caret: Caret): Int = fromDocumentOffset(caret.offset) { caret.visualPosition }

  fun getSelectionStart(): Int {
    val caret = editor.caretModel.currentCaret
    return if (caret.hasSelection()) fromDocumentOffset(caret.selectionStart) else getCaretOffset(caret)
  }

  fun getSelectionEnd(): Int {
    val caret = editor.caretModel.currentCaret
    return if (caret.hasSelection()) fromDocumentOffset(caret.selectionEnd) else getCaretOffset(caret)
  }

  fun getSelectedText(): String? {
    val selectionModel = editor.selectionModel
    // the selection model also gives a block selection and virtual space
    if (folds().isEmpty() || !selectionModel.hasSelection()) return selectionModel.selectedText
    return getText(getSelectionStart(), getSelectionEnd())
  }

  fun getOffsetAt(point: Point): Int {
    val documentOffset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
    return fromDocumentOffset(documentOffset) { editor.xyToVisualPosition(point) }
  }

  fun offsetToXY(offset: Int): Point {
    val folds = folds()
    val fold = folds.getOrNull(folds.lastIndexAtOrBefore(offset) { it.visibleStart })
    if (fold != null && offset < fold.visibleEnd) {
      val foldStart = editor.offsetToVisualPosition(fold.start)
      return editor.visualPositionToXY(VisualPosition(foldStart.line, foldStart.column + offset - fold.visibleStart))
    }
    return editor.logicalPositionToXY(editor.offsetToLogicalPosition(toDocumentOffset(folds, offset, false)))
  }

  /** Calls [listener] on the EDT when the text changes, but the document does not. A fold region that collapses does this. */
  fun addChangeListener(listener: Runnable, parentDisposable: Disposable) {
    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() {
        if (ScreenReader.isActive()) listener.run()
      }
    }, parentDisposable)
  }

  private fun folds(): Array<Fold> {
    // Without an active screen reader, accessible text matches the document text.
    // During a bulk update, the fold cache is not refreshed, so accessible text uses the document text.
    if (!ScreenReader.isActive() || document.isInBulkUpdate) return NO_FOLDS
    val regions = editor.foldingModel.fetchTopLevel()
    if (regions.isNullOrEmpty()) return NO_FOLDS
    snapshot?.let { if (it.regions === regions) return it.folds }

    var shift = 0
    val folds = regions.filter { it.isValid }.map { region ->
      val start = region.startOffset
      val end = region.endOffset
      // a placeholder takes one visual line, so its line breaks are not line breaks of this text
      val placeholder = region.placeholderText.replace('\n', ' ').replace('\r', ' ')
      Fold(start, end, placeholder, start + shift).also { shift += placeholder.length - (end - start) }
    }.toTypedArray()
    snapshot = Snapshot(regions, folds)
    return folds
  }

  private fun toDocumentOffset(folds: Array<Fold>, offset: Int, isRangeEnd: Boolean): Int {
    // a range that ends at the start of a placeholder does not include the fold region
    val fold = folds.getOrNull(folds.lastIndexAtOrBefore(if (isRangeEnd) offset - 1 else offset) { it.visibleStart }) ?: return offset
    return when {
      offset >= fold.visibleEnd -> fold.end + offset - fold.visibleEnd
      isRangeEnd -> fold.end
      else -> fold.start
    }
  }

  private fun fromDocumentOffset(folds: Array<Fold>, documentOffset: Int): Int {
    val fold = folds.getOrNull(folds.lastIndexAtOrBefore(documentOffset) { it.start }) ?: return documentOffset
    return when {
      documentOffset == fold.start -> fold.visibleStart
      documentOffset < fold.end -> fold.visibleEnd
      else -> fold.visibleEnd + documentOffset - fold.end
    }
  }

  private inline fun fromDocumentOffset(documentOffset: Int, position: () -> VisualPosition): Int {
    val folds = folds()
    val offset = fromDocumentOffset(folds, documentOffset)
    val fold = folds.getOrNull(folds.lastIndexAtOrBefore(documentOffset) { it.start })
    if (fold == null || fold.start != documentOffset) return offset
    val foldStart = editor.offsetToVisualPosition(documentOffset)
    val visualPosition = position()
    if (visualPosition.line != foldStart.line) return offset
    return offset + (visualPosition.column - foldStart.column).coerceIn(0, fold.placeholder.length)
  }

  private companion object {
    private val NO_FOLDS = emptyArray<Fold>()
  }
}

/** The index of the last fold whose key is at most [value], or -1. The keys do not decrease. */
private inline fun <T> Array<T>.lastIndexAtOrBefore(value: Int, key: (T) -> Int): Int {
  var low = 0
  var high = size
  while (low < high) {
    val mid = (low + high) ushr 1
    if (key(this[mid]) <= value) low = mid + 1 else high = mid
  }
  return low - 1
}
