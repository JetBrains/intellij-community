// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import java.awt.FontMetrics
import javax.swing.JComponent

internal interface TitlePart {
  enum class State {
    LONG,
    MIDDLE,
    SHORT,
    HIDE,
    IGNORED
  }

  var active: Boolean

  val longWidth: Int
  val shortWidth: Int

  val toolTipPart: String

  fun refresh(label: JComponent, fm: FontMetrics)

  fun getLong(): String
  fun getShort(): String
}

internal interface BaseTitlePart : TitlePart {
  var longText: String
  var shortText: String
}

internal interface ShrinkingTitlePart : TitlePart {
  fun shrink(label: JComponent, fm: FontMetrics, maxWidth: Int): String
}