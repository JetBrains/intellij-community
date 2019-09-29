// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.DocumentUtil
import kotlin.math.max
import kotlin.math.min

/**
 * @author Marc Knaup
 */
open class SortLinesAction(comparator: Comparator<String>) : TextComponentEditorAction(
  Handler(comparator = emptyLast(comparator))
) {

  class Alphabetically : SortLinesAction(
    comparator = compareAlphabetically(ignoreCase = true)
      .then(compareAlphabetically(ignoreCase = false))
  )

  class AlphabeticallyCaseSensitive : SortLinesAction(
    comparator = compareAlphabetically(ignoreCase = false)
  )

  class AlphabeticallyDescending : SortLinesAction(
    comparator = compareAlphabetically(ignoreCase = true)
      .then(compareAlphabetically(ignoreCase = false))
      .reversed()
  )

  class AlphabeticallyCaseSensitiveDescending : SortLinesAction(
    comparator = compareAlphabetically(ignoreCase = false)
      .reversed()
  )

  class ByLength : SortLinesAction(
    comparator = compareByLength()
  )

  class ByLengthDescending : SortLinesAction(
    comparator = compareByLength()
      .reversed()
  )

  class Naturally : SortLinesAction(
    comparator = compareNaturally()
      .then(compareAlphabetically(ignoreCase = false))
  )

  class NaturallyDescending : SortLinesAction(
    comparator = compareNaturally()
      .then(compareAlphabetically(ignoreCase = false))
      .reversed()
  )


  override fun update(event: AnActionEvent) {
    super.update(event)

    event.presentation.isEnabledAndVisible = getEditor(event.dataContext)?.selectionModel?.hasSelection() ?: false
  }


  companion object {

    private fun compareAlphabetically(ignoreCase: Boolean) =
      Comparator<String> { a, b -> a.compareTo(b, ignoreCase = ignoreCase) }


    private fun compareByLength() =
      compareBy<String> { it.length }


    private fun compareNaturally(): Comparator<String> =
      NaturalComparator.INSTANCE


    private fun emptyLast(next: Comparator<String>) = Comparator<String> { a, b ->
      when {
        a === b -> 0
        a.isEmpty() && b.isEmpty() -> 0
        a.isEmpty() -> 1
        b.isEmpty() -> -1
        else -> next.compare(a, b)
      }
    }
  }


  private class Handler internal constructor(
    private val comparator: Comparator<String>
  ) : EditorWriteActionHandler(true) {

    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      with(editor) {
        sortLines(visualRange = getVisualSelectionRange() ?: return, caret = caret)
      }
    }


    private fun Editor.getVisualSelectionRange(): Pair<VisualPosition, VisualPosition>? {
      if (!selectionModel.hasSelection()) {
        return null
      }

      val selectionStart = selectionModel.selectionStart
      var selectionEnd = selectionModel.selectionEnd
      if (selectionEnd > selectionStart && DocumentUtil.isAtLineStart(selectionEnd, document)) {
        selectionEnd--
      }

      return offsetToVisualPosition(min(selectionStart, selectionEnd)) to
        offsetToVisualPosition(max(selectionStart, selectionEnd))
    }


    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext) = with(editor) {
      selectionModel.hasSelection() && !isOneLineMode && !isViewer
    }


    private fun CharSequence.endIndexOfLastNonBlankLine(start: Int, end: Int): Int {
      var currentLineEnd = end
      for (offset in end - 1 downTo start) {
        val character = this[offset]
        when {
          character == '\n' ->
            currentLineEnd = offset

          !character.isWhitespace() ->
            return currentLineEnd
        }
      }

      return -1
    }


    private fun CharSequence.startIndexOfFirstNonBlankLine(start: Int, end: Int): Int {
      var currentLineStart = start
      for (offset in start until end) {
        val character = this[offset]
        when {
          character == '\n' ->
            currentLineStart = offset + 1

          !character.isWhitespace() ->
            return currentLineStart
        }
      }

      return -1
    }


    private fun Editor.sortLines(visualRange: Pair<VisualPosition, VisualPosition>, caret: Caret?) {
      val (firstLineLogicalStart, lastLineLogicalEnd) =
        EditorUtil.calcSurroundingRange(this, visualRange.first, visualRange.second)

      val characters = document.charsSequence

      var firstLineStart = logicalPositionToOffset(firstLineLogicalStart)
      var lastLineEnd = logicalPositionToOffset(lastLineLogicalEnd)

      firstLineStart = characters.startIndexOfFirstNonBlankLine(start = firstLineStart, end = lastLineEnd)
      if (firstLineStart < 0) {
        return
      }

      lastLineEnd = characters.endIndexOfLastNonBlankLine(start = firstLineStart, end = lastLineEnd)
      if (lastLineEnd < 0) {
        return
      }

      val firstLineNumber = document.getLineNumber(firstLineStart)

      val sortedLines = characters.subSequence(firstLineStart, lastLineEnd)
        .lines()
        .mapIndexed { index, content ->
          Line(
            number = firstLineNumber + index,
            content = content,
            trimmedContent = content.trim()
          )
        }
        .sortedWith(Comparator { a, b ->
          comparator.compare(a.trimmedContent, b.trimmedContent)
        })

      val orderHasChanged = sortedLines.withIndex().any { (index, line) -> line.number != firstLineNumber + index }
      if (orderHasChanged) {
        val updateCaretLine: (() -> Unit)? = caret?.let {
          val lineNumber = caret.logicalPosition.line
          val offsetFromLineStart = caret.offset - document.getLineStartOffset(lineNumber)

          return@let {
            val index = sortedLines.indexOfFirst { it.number == lineNumber }
            if (index >= 0) {
              val newLineStart = document.getLineStartOffset(index + firstLineNumber)

              caret.moveToOffset(offsetFromLineStart + newLineStart)
            }
          }
        }

        document.replaceString(firstLineStart, lastLineEnd, sortedLines.joinToString("\n") { it.content })

        updateCaretLine?.invoke()
      }

      if (lastLineEnd < characters.length)
        lastLineEnd += 1

      selectionModel.setSelection(firstLineStart, lastLineEnd)
    }
  }


  private class Line(
    val number: Int,
    val content: String,
    val trimmedContent: String
  )
}
