// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import javax.swing.JComponent

interface TitlePart {
  enum class State {
    LONG,
    MIDDLE,
    SHORT,
    HIDE,
    IGNORED
  }

  var active: Boolean
  val component: JComponent


  val longWidth: Int
  val shortWidth: Int

  val toolTipPart: String
  val isClipped: Boolean

  fun refresh()

  fun hide()

  fun showLong()
  fun showShort()

  fun setToolTip(value: String?)
}

interface BaseTitlePart : TitlePart {
  var longText: String
  var shortText: String
}

interface ShrinkingTitlePart : TitlePart {
  fun shrink(maxWidth: Int): Int
}