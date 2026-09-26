// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

interface EdgePrintElement : PrintElement {
  val positionInOtherRow: Int
  val type: Type
  val lineStyle: LineStyle

  fun hasArrow(): Boolean

  enum class Type {
    UP,
    DOWN
  }

  enum class LineStyle {
    SOLID,
    DASHED,
    DOTTED
  }
}
