// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import kotlin.math.abs
import kotlin.math.sign


/**
 * Relative lines are defined here the same way they are in Vim:
 * - We do not increment the counter for wrapped lines (only logical lines are counted)
 * - We do not increment the counter for collapsed lines and skip them
 */
object RelativeLineHelper {
  /**
   * @param editor the target editor.
   * @param caretLine the logical line that serves as the reference point (zero) for relative numeration.
   * @param logicalLine the logical line for which we want to determine the relative position.
   * @return the relative number of the logical line, counted from the caret line.
   * Same as relative numeration, but returns current line number if [logicalLine] is equal [caretLine] or is located in the same collapsed fold region.
   */
  fun getHybridLine(editor: Editor, caretLine: Int, logicalLine: Int): Int {
    if (caretLine == logicalLine) return logicalLine + 1 // converted 0-based number to 1-based

    val caretFoldLineRange = getLogicalLineRangeInVisualLine(editor, caretLine)
    return if (logicalLine in caretFoldLineRange) {
      caretFoldLineRange.first + 1 // converted 0-based number to 1-based
    } else {
      getRelativeLine(editor, caretLine, logicalLine)
    }
  }

  /**
   * @param editor the target editor.
   * @param caretLine the logical line that serves as the reference point (zero) for relative numeration.
   * @param logicalLine the logical line for which we want to determine the relative position.
   * @return the relative number of the logical line, counted from the caret line.
   * It is negative for lines above the caret line and positive otherwise.
   */
  fun getRelativeLine(editor: Editor, caretLine: Int, logicalLine: Int): Int {
    val foldingModel = editor.foldingModel as FoldingModelImpl
    val caretOffset = getOffsetAtFoldStart(editor, caretLine)
    val lineOffset = getOffsetAtFoldStart(editor, logicalLine)
    val foldedBeforeCaret = foldingModel.getFoldedLinesCountBefore(caretOffset)
    val foldedBeforeLine = foldingModel.getFoldedLinesCountBefore(lineOffset)

    // same lines, but moved to fold start if necessary
    val caretLineAtFoldStart = editor.offsetToLogicalPosition(caretOffset).line
    val logicalLineAtFoldStart = editor.offsetToLogicalPosition(lineOffset).line

    return logicalLineAtFoldStart - caretLineAtFoldStart - (foldedBeforeLine - foldedBeforeCaret)
  }

  /**
   * @param editor the target editor.
   * @param caretLine the logical line that serves as the reference point (zero) for relative numeration.
   * @param relativeLine the distance from the logical line to the caretLine.
   * It is negative for lines above the caret line and positive otherwise.
   * @return the logical line that is considered to be [relativeLine] for [caretLine].
   * Please note that the line is NOT normalized.
   * It can be negative or exceed the number of lines in the file.
   */
  fun getLogicalLine(editor: Editor, caretLine: Int, relativeLine: Int): Int {
    var relativeDistance = 0
    val step = relativeLine.sign
    val shouldHandleFolds = checkIfShouldCheckForFolds(editor, caretLine, step)
    return if (!shouldHandleFolds) {
      caretLine + relativeLine
    } else {
      var line = getFoldBorderLine(editor, caretLine, step)
      while (relativeDistance < abs(relativeLine)) {
        line = getFoldBorderLine(editor, line + step, step)
        ++relativeDistance
      }
      // here fold start line is returned
      getFoldBorderLine(editor, line, -1)
    }
  }

  private fun getOffsetAtFoldStart(editor: Editor, line: Int): Int {
    val document = editor.document
    val lineStartOffset = document.getLineStartOffset(line)
    return EditorUtil.getNotFoldedLineStartOffset(editor, lineStartOffset)
  }

  private fun checkIfShouldCheckForFolds(editor: Editor, caretLine: Int, step: Int): Boolean {
    val document = editor.document
    val foldingModelImpl = editor.foldingModel as FoldingModelImpl
    val caretLineOffset = if (step < 0) document.getLineEndOffset(caretLine) else document.getLineStartOffset(caretLine)
    if (foldingModelImpl.isOffsetCollapsed(caretLineOffset)) return true

    val foldedLinesBeforeCaret = foldingModelImpl.getFoldedLinesCountBefore(caretLineOffset)
    val foldedLinesAfterCaret = foldingModelImpl.totalNumberOfFoldedLines - foldedLinesBeforeCaret
    return if (step < 0) foldedLinesBeforeCaret != 0 else foldedLinesAfterCaret != 0
  }

  /**
   * Retrieves either the first or the last line of a fold containing the specified [line].
   *
   * @param line  The line within a fold for which the first or last line is to be retrieved.
   * @param step  The direction of retrieval.
   * If `step > 0`, the first line of the fold is returned.
   * If `step < 0`, the last line of the fold is returned.
   *
   * @return The first or last line of the fold containing the specified [line],
   * based on the [step] value.
   * If [line] is not part of a fold, the 'line' itself is returned.
   */
  private fun getFoldBorderLine(editor: Editor, line: Int, step: Int): Int {
    val fold = getLogicalLineRangeInVisualLine(editor, line)
    return if (step < 0) fold.first else fold.last
  }

  /**
   * Gets a range of lines in one visual line without taking wraps into account
   * @param editor the target editor
   * @param line a logical line inside the visual line
   */
  private fun getLogicalLineRangeInVisualLine(editor: Editor, line: Int): IntRange {
    val lineOffset = editor.document.getLineStartOffset(line)
    val foldRegionStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, lineOffset)
    val foldRegionEndOffset = EditorUtil.getNotFoldedLineEndOffset(editor, lineOffset)

    val firstLine = editor.offsetToLogicalPosition(foldRegionStartOffset).line
    val lastLine = editor.offsetToLogicalPosition(foldRegionEndOffset).line

    return firstLine .. lastLine
  }
}