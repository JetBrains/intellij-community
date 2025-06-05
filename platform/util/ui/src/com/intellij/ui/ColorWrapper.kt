// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.ui.ComparableColor
import com.intellij.util.ui.PresentableColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
@Suppress("UseJBColor")
abstract class ColorWrapper(color: Color) : Color(color.rgb, true), PresentableColor, ComparableColor {
  override fun darker(): Color {
    return SwingTuneDarker(this).createColor(true)
  }

  override fun brighter(): Color {
    return SwingTuneBrighter(this).createColor(true)
  }
}
