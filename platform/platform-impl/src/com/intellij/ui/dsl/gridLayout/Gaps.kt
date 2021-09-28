// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class Gaps(val top: Int = 0, val left: Int = 0, val bottom: Int = 0, val right: Int = 0) {
  companion object {
    val EMPTY = Gaps(0)
  }

  init {
    checkNonNegative(::top)
    checkNonNegative(::left)
    checkNonNegative(::bottom)
    checkNonNegative(::right)
  }

  constructor(size: Int) : this(size, size, size, size)

  val width: Int
    get() = left + right

  val height: Int
    get() = top + bottom
}
