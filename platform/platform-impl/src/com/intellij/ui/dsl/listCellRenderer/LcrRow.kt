// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import javax.swing.JList

@ApiStatus.Experimental
@LcrDslMarker
interface LcrRow<T> {

  /**
   * Adds a cell with an icon
   */
  fun icon(init: (LcrInitParams.() -> Unit)? = null): LcrIcon

  /**
   * Adds a cell with a text
   */
  fun text(init: (LcrTextInitParams.() -> Unit)? = null): LcrText

  /**
   * Register a renderer
   * * One (and only one) renderer must be provided for every [listCellRenderer]
   * * The renderer should configure cells, defined by [icon] and [text] methods
   * * The initial state for all cells are set before every render invocation
   */
  fun renderer(init: (list: JList<out T>, value: T, index: Int, isSelected: Boolean, cellHasFocus: Boolean) -> Unit)

  /**
   * Simplified version of overloaded method
   */
  fun renderer(init: (value: T) -> Unit)
}