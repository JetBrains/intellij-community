// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table

import java.awt.Cursor
import java.awt.event.MouseEvent

interface VcsLogCellController {
  fun performMouseClick(row: Int, e: MouseEvent): Cursor?

  fun performMouseMove(row: Int, e: MouseEvent): MouseMoveResult

  fun shouldSelectCell(row: Int, e: MouseEvent): Boolean = true

  data class MouseMoveResult(val cursor: Cursor?, val continueProcessing: Boolean = true) {
    companion object {
      @JvmField
      val DEFAULT = fromCursor(Cursor.DEFAULT_CURSOR)

      @JvmStatic
      fun fromCursor(cursor: Int): MouseMoveResult = MouseMoveResult(Cursor.getPredefinedCursor(cursor), false)
    }
  }
}
