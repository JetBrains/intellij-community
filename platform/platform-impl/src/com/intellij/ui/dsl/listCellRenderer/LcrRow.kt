// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon
import javax.swing.JList

@ApiStatus.Experimental
@LcrDslMarker
interface LcrRow<T> {

  enum class Gap {
    /**
     * Default gap between cells. Usages:
     * * Gap between icon and related text
     */
    DEFAULT,

    /**
     * No space
     */
    NONE
  }

  val list: JList<out T>
  val value: T
  val index: Int
  val selected: Boolean
  val hasFocus: Boolean

  /**
   * Row background. Used if the row is not selected and on left/right sides of selected row (new UI only)
   */
  var background: Color?

  /**
   * Selection color if the row is selected or `null` otherwise
   */
  var selectionColor: Color?


  /**
   * The gap between the previous cell and the next one. Not used for the first cell
   */
  fun gap(gap: Gap)

  /**
   * Adds a cell with an icon
   */
  fun icon(icon: Icon, init: (LcrIconInitParams.() -> Unit)? = null)

  /**
   * Adds a cell with a text
   */
  fun text(text: @Nls String, init: (LcrTextInitParams.() -> Unit)? = null)

}