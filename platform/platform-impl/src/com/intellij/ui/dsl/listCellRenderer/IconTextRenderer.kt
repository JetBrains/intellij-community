// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import org.jetbrains.annotations.ApiStatus
import javax.swing.JList

@ApiStatus.Experimental
interface IconTextRenderer<T> {

  val icon: LcrIcon
  val text: LcrText

  val list: JList<out T>
  val value: T
  val index: Int
  val isSelected: Boolean
  val cellHasFocus: Boolean
  val rowParams: RowParams
}
