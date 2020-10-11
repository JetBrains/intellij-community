// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.title

import com.intellij.ui.awt.RelativeRectangle
import javax.swing.JComponent

internal interface CustomHeaderTitle {
  var onBoundsChanged: (() -> Unit)?
  val view: JComponent
  fun setActive(value: Boolean)
  fun getBoundList() : List<RelativeRectangle> = emptyList()
}