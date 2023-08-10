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

  val list: JList<out T>
  val value: T
  val index: Int
  val selected: Boolean
  val hasFocus: Boolean

  /**
   * Row background
   */
  var background: Color?

  /**
   * Adds a cell with an icon
   */
  fun icon(icon: Icon, init: (LcrIconInitParams.() -> Unit)? = null)

  /**
   * Adds a cell with a text
   */
  fun text(text: @Nls String, init: (LcrTextInitParams.() -> Unit)? = null)

}