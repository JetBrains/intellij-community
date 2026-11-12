// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus

/**
 * The setup of the character grid mode of an editor.
 *
 * The spec is the editor identity of the grid. [EditorImpl.setCharacterGridSpec] takes it in one call, and
 * [EditorImpl.getCharacterGrid] gives the facade of the grid for as long as the spec is set.
 *
 * The cell rule: a code point takes the cells of [cells], and a cluster takes the sum of the cells of its code points.
 * The grid counts code points, so the low surrogate of a pair takes no cell of its own.
 *
 * The known limit: the rule reads one code point at a time. A terminal emulator can count other cells for a grapheme
 * cluster, for a regional indicator pair, or for a variation selector.
 *
 * @property columnSpacing the width of one cell, as a multiple of the width estimate of the widest editor char
 * @property doubleWidthCharacterStrategy the rule that gives a code point two cells
 */
@ApiStatus.Internal
data class CharacterGridSpec(
  val columnSpacing: Float,
  val doubleWidthCharacterStrategy: DoubleWidthCharacterStrategy,
) {
  init {
    require(columnSpacing > 0 && columnSpacing.isFinite()) {
      "The column spacing must be a positive finite number, and it is $columnSpacing"
    }
  }

  /**
   * Returns the number of cells of the code point: two cells for a double-width code point, and one cell for each
   * other code point.
   */
  fun cells(codePoint: Int): Int = if (doubleWidthCharacterStrategy.isDoubleWidth(codePoint)) 2 else 1
}
