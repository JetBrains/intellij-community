// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import kotlin.math.abs
import kotlin.math.sign


/**
 * Relative lines are defined here the same way they are in Vim:
 * <ul>
 *   <li>We do not increment the counter for wrapped lines (only logical lines are counted)</li>
 *   <li>We do not increment the counter for collapsed lines and skip them</li>
 * </ul>
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
    var currentLine = caretLine
    var currentRelativeDistance = 0
    val step = relativeLine.sign
    while (currentRelativeDistance < abs(relativeLine)) {
      currentLine += step
      val offset = editor.logicalPositionToOffset(LogicalPosition(currentLine, 0))
      if (!editor.foldingModel.isOffsetCollapsed(offset)) {
        currentRelativeDistance += 1
      }
    }
    return currentLine
  }

  /**
   * @param editor the target editor.
   * @param caretLine the logical line that serves as the reference point (zero) for relative numeration.
   * @param logicalLine the logical line for which we want to determine the relative position.
   * @return the relative number of the logical line, counted from the caret line.
   * It is negative for lines above the caret line and positive otherwise.
   */
  fun getRelativeLine(editor: Editor, caretLine: Int, logicalLine: Int): Int {
    var currentRelativeLine = 0
    var currentLine = caretLine
    val step = (logicalLine - caretLine).sign
    while (currentLine != logicalLine) {
      val offset = editor.logicalPositionToOffset(LogicalPosition(currentLine, 0))
      if (!editor.foldingModel.isOffsetCollapsed(offset)) {
        currentRelativeLine += step
      }
      currentLine += step
    }
    return currentRelativeLine
  }
}