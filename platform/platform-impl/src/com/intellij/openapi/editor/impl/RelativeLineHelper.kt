// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
   * @param relativeLine the distance from the logical line to the caretLine.
   * It is negative for lines above the caret line and positive otherwise.
   * @return the logical line that is considered to be [relativeLine] for [caretLine].
   * Please note that the line is NOT normalized. It can be negative or exceed the number of lines in the file.
   */
  fun getLogicalLine(editor: Editor, caretLine: Int, relativeLine: Int): Int {
    var relativeDistance = 0
    val step = relativeLine.sign
    val shouldHandleFolds = checkIfShouldCheckForFolds(editor, caretLine, step)

    var line = getFoldBorderLine(editor, caretLine, step)
    while (relativeDistance < abs(relativeLine)) {
      line += step
      if (shouldHandleFolds) {
        line = getFoldBorderLine(editor, line, step)
      }
      ++relativeDistance
    }
    // here return fold start line
    return getFoldBorderLine(editor, line, -1)
  }

  /**
   * @param editor the target editor.
   * @param caretLine the logical line that serves as the reference point (zero) for relative numeration.
   * @param logicalLine the logical line for which we want to determine the relative position.
   * @return the relative number of the logical line, counted from the caret line.
   * Same as relative numeration, but returns current line number if [logicalLine] is equal [caretLine] or is located in the same collapsed fold region.
   */
  fun getHybridLine(editor: Editor, caretLine: Int, logicalLine: Int): Int {
    if (caretLine == logicalLine) return logicalLine + 1 // converted 0-based number to 1-based

    val caretFoldLineRange = getFoldLineRangeForLine(editor, caretLine)
    return if (caretFoldLineRange != null && logicalLine in caretFoldLineRange) {
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
    val step = (logicalLine - caretLine).sign
    if (step == 0) return 0
    val shouldHandleFolds = checkIfShouldCheckForFolds(editor, caretLine, step)

    var relativeLineCount = 0
    var line = getFoldBorderLine(editor, caretLine, step) + step
    val lineRange = min(caretLine, logicalLine) .. max(caretLine, logicalLine)
    while (line in lineRange) {
      if (shouldHandleFolds) {
        line = getFoldBorderLine(editor, line, step)
      }
      line += step
      ++relativeLineCount
    }
    return relativeLineCount
  }

  private fun checkIfShouldCheckForFolds(editor: Editor, caretLine: Int, step: Int): Boolean {
    editor.settings
    val foldingModelImpl = editor.foldingModel as FoldingModelImpl
    val caretLineOffset = editor.document.getLineStartOffset(caretLine)
    val foldedLinesBeforeCaret = foldingModelImpl.getFoldedLinesCountBefore(caretLineOffset)
    val foldedLinesAfterCaret = foldingModelImpl.totalNumberOfFoldedLines - foldedLinesBeforeCaret
    return if (step < 0) foldedLinesBeforeCaret != 0 else foldedLinesAfterCaret != 0
  }

  /**
   * Retrieves either the first or the last line of a fold containing the specified [line].
   *
   * @param line  The line within a fold for which the first or last line is to be retrieved.
   * @param step  The direction of retrieval.
   * If step > 0, the first line of the fold is returned.
   * If step < 0, the last line of the fold is returned.
   *
   * @return The first or last line of the fold containing the specified [line],
   * based on the [step] value.
   * If [line] is not part of a fold, the 'line' itself is returned.
   */
  private fun getFoldBorderLine(editor: Editor, line: Int, step: Int): Int {
    val fold = getFoldLineRangeForLine(editor, line) ?: return line
    return if (step < 0) fold.first else fold.last
  }

  private fun getFoldLineRangeForLine(editor: Editor, line: Int): IntRange? {
    if (line !in (0 until editor.document.lineCount)) return null
    val startOffset = editor.document.getLineStartOffset(line)
    var foldRegion = editor.foldingModel.getCollapsedRegionAtOffset(startOffset)?.toLineRange(editor)
    if (foldRegion != null) return foldRegion

    val endOffset = editor.document.getLineEndOffset(line)
    foldRegion = editor.foldingModel.getCollapsedRegionAtOffset(endOffset)?.toLineRange(editor)
    return foldRegion
  }

  private fun FoldRegion.toLineRange(editor: Editor): IntRange {
    val startLine = editor.offsetToLogicalPosition(this.startOffset).line
    val endLine = editor.offsetToLogicalPosition(this.endOffset).line
    return startLine .. endLine
  }
}