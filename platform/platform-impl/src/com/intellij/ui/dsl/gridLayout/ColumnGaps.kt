// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

data class ColumnGaps(val left: Int = 0, val right: Int = 0) {
  companion object {
    val EMPTY = ColumnGaps()
  }

  init {
    checkNonNegative(::left)
    checkNonNegative(::right)
  }

  val width: Int
    get() = left + right
}
