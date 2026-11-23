// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.codePointAt
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

/**
 * The read API of the character grid mode of the editor, see [CharacterGridSpec].
 *
 * [EditorImpl.getCharacterGrid] gives the facade, and it gives the same instance for as long as the grid mode stays on.
 * Each read gives the current value. The cell width follows the editor fonts and the spec, and the columns and the
 * rows follow the visible area.
 *
 * To change the grid, give [EditorImpl.setCharacterGridSpec] a new spec. The facade only reads.
 */
@ApiStatus.Internal
interface CharacterGrid {

  /**
   * The current size of the visible character grid in columns.
   *
   * Is equal to zero if the editor has no size yet.
   */
  val columns: Int

  /**
   * The current size of the visible character grid in rows.
   *
   * Is equal to zero if the editor has no size yet.
   */
  val rows: Int

  /**
   * The width of a single-width character in pixels, which is the width of one cell.
   *
   * The width is the width estimate of the widest editor character times the column spacing of the spec. It comes
   * from the editor fonts alone, so the font families of the text do not change it.
   */
  val charWidth: Float

  /**
   * The strategy to differentiate between single and double width characters.
   *
   * The strategy comes from [CharacterGridSpec], so it stays the same for as long as the spec stays the same.
   * To change it, give [EditorImpl.setCharacterGridSpec] a new spec.
   */
  val doubleWidthCharacterStrategy: DoubleWidthCharacterStrategy

  /**
   * The width of the given character in pixels.
   *
   * Will always be either equal to the grid cell width or twice that value if it's a double-width character.
   *
   * @see doubleWidthCharacterStrategy
   */
  fun codePointWidth(codePoint: Int): Float

  /**
   * Creates a cell iterator for the given document range.
   *
   * The iterator will be positioned on [startOffset], the column number will be initialized to zero.
   */
  fun iterator(startOffset: Int, endOffset: Int): CharacterGridIterator

}

/**
 * An iterator over grid cells in some document range.
 *
 * At any given moment the iterator is positioned over some cell.
 * The cell has a start offset ([cellStartOffset]), an end offset ([cellEndOffset]),
 * a start column ([cellStartColumn]) and an end column ([cellEndColumn]).
 * The start offset is the offset of the code point corresponding to the current character.
 * The end offset will be the start offset plus one or two depending on whether
 * the current code point corresponds to a BMP or an SMP character (a surrogate pair).
 * The start column is the number of columns to the left of the character, zero for the first character in a line.
 * The end column will be the start column plus one or two depending on whether the current character is a double-width one.
 *
 * A special case is when an iterator is positioned over a line break.
 * Then the start offset will be the offset just past the end of the line,
 * the end offset will be the start offset plus one or two (in the case of a CRLF line break).
 * The start column will be the number of cells in the line,
 * the end column will be zero.
 */
@ApiStatus.Internal
interface CharacterGridIterator {
  /**
   * The iterator reached the end offset.
   */
  val isAtEnd: Boolean

  /**
   * The iterator is currently at a line break.
   */
  val isLineBreak: Boolean

  /**
   * The current cell start offset.
   */
  val cellStartOffset: Int

  /**
   * The current cell end offset.
   */
  val cellEndOffset: Int

  /**
   * The current cell start column.
   */
  val cellStartColumn: Int

  /**
   * The current cell end column.
   */
  val cellEndColumn: Int

  /**
   * Advances by one cell.
   */
  fun advance()
}

internal class CharacterGridImpl(
  private val editor: EditorImpl,
) : CharacterGrid {

  private val view: EditorView
    get() = editor.view

  private val spec: CharacterGridSpec
    get() = checkNotNull(editor.characterGridSpec) {
      "The editor must be in the grid mode to use a character grid"
    }

  private val size: Dimension
    get() = editor.scrollingModel.visibleArea.size

  override val charWidth: Float
    get() = view.metrics.maxCharWidth() * spec.columnSpacing

  override val columns: Int
    get() {
      val width = size.width
      return if (width > 0) (width.toFloat() / charWidth).toInt() else 0
    }

  override val rows: Int
    get() {
      val lineHeight = view.lineHeight
      val height = size.height
      return if (height > 0) (height.toFloat() / lineHeight.toFloat()).toInt() else 0
    }

  override val doubleWidthCharacterStrategy: DoubleWidthCharacterStrategy
    get() = spec.doubleWidthCharacterStrategy

  override fun codePointWidth(codePoint: Int): Float {
    return spec.cells(codePoint) * charWidth
  }

  override fun iterator(startOffset: Int, endOffset: Int): CharacterGridIterator =
    CharacterGridIteratorImpl(editor.document.immutableCharSequence, spec, startOffset, endOffset)

}

private class CharacterGridIteratorImpl(
  text: @NlsSafe CharSequence,
  private val spec: CharacterGridSpec,
  private val startOffset: Int,
  private val endOffset: Int,
) : CharacterGridIterator {

  private val substring: String = text.substring(startOffset, endOffset)
  private val textLength: Int = text.length

  private var codePoint: Int = 0

  override var isAtEnd: Boolean = false
    private set

  override var isLineBreak: Boolean = false
    private set

  override var cellStartOffset: Int = startOffset
    private set

  override var cellEndOffset: Int = 0
    private set

  override var cellStartColumn: Int = 0
    private set

  override var cellEndColumn: Int = 0
    private set

  init {
    setOffset(startOffset)
    advanceColumn()
  }

  override fun advance() {
    setOffset(cellEndOffset)
    advanceColumn()
  }

  private fun setOffset(newOffset: Int) {
    if (newOffset >= endOffset) {
      codePoint = 0
      isAtEnd = true
      isLineBreak = true
      cellStartOffset = endOffset
      cellEndOffset = endOffset
    }
    else {
      val char = substring.codePointAt(newOffset - startOffset)
      codePoint = char
      isAtEnd = false
      isLineBreak = char == '\n'.code || char == '\r'.code
      val bmp = Character.isBmpCodePoint(char)
      cellStartOffset = newOffset
      cellEndOffset = if (bmp) {
        if (char == '\r'.code && newOffset + 1 < textLength && substring[newOffset - startOffset + 1] == '\n') {
          newOffset + 2 // the \r\n case
        }
        else {
          newOffset + 1 // a regular BMP char
        }
      }
      else {
        newOffset + 2 // a surrogate pair
      }
    }
  }

  private fun advanceColumn() {
    cellStartColumn = cellEndColumn
    if (isLineBreak) {
      cellEndColumn = 0
    }
    else {
      cellEndColumn += spec.cells(codePoint)
    }
  }
}
