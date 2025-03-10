// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

/**
 * The facade of all things related to the character grid mode of the editor.
 *
 * Accessed with [EditorImpl.getCharacterGrid].
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
   * The strategy to differentiate between single and double width characters.
   *
   *   If not set, then the character width will be guessed by its actual width in the font used.
   *   Such heuristics generally works well for most regular characters,
   *   but may fail for special characters sometimes used in fancy shell prompts, for example,
   *   which is why an explicit strategy is needed.
   *
   * @param doubleWidthCharacterStrategy the strategy to use
   */
  var doubleWidthCharacterStrategy: DoubleWidthCharacterStrategy

  /**
   * The width of the given character in pixels.
   *
   * Will always be either equal to the grid cell width or twice that value if it's a double-width character.
   *
   * @see doubleWidthCharacterStrategy
   */
  fun codePointWidth(codePoint: Int): Float

}

internal class CharacterGridImpl(
  private val editor: EditorImpl,
) : CharacterGrid {

  private val view: EditorView
    get() = editor.view

  private val columnSpacing: Float
    get() = checkNotNull(editor.settings.characterGridWidthMultiplier) {
      "The editor must be in the grid mode to create an instance of a character grid"
    }

  private val size: Dimension
    get() = editor.scrollingModel.visibleArea.size

  private val charWidth: Float
    get() = view.maxCharWidth * columnSpacing

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

  override var doubleWidthCharacterStrategy: DoubleWidthCharacterStrategy by view::doubleWidthCharacterStrategy

  override fun codePointWidth(codePoint: Int): Float {
    return (if (doubleWidthCharacterStrategy.isDoubleWidth(codePoint)) 2.0f else 1.0f) * charWidth
  }

}
