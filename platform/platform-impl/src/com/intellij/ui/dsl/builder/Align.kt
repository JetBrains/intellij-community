// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.builder.impl.AlignBothImpl

sealed interface Align

sealed interface AlignX : Align {
  object LEFT : AlignX
  object CENTER : AlignX
  object RIGHT : AlignX
  object FILL : AlignX
}

sealed interface AlignY : Align {
  object TOP : AlignY
  object CENTER : AlignY
  object BOTTOM : AlignY
  object FILL : AlignY
}

interface AlignBoth : Align {
  companion object {
    @JvmField
    val FILL = AlignX.FILL + AlignY.FILL

    @JvmField
    val CENTER = AlignX.CENTER + AlignY.CENTER
  }
}

operator fun AlignX.plus(alignY: AlignY): AlignBoth {
  return AlignBothImpl(this, alignY)
}