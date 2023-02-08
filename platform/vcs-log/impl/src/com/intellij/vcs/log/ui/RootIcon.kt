// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import java.awt.Color

object RootIcon {
  @JvmStatic
  fun create(
    color: Color,
  ): ColorIcon {
    val size = iconSize
    val arc = if (ExperimentalUI.isNewUI()) arcSize else 0

    return ColorIcon(size, size, size, size, color, false, arc)
  }

  @JvmStatic
  fun createAndScale(color: Color): ColorIcon = JBUIScale.scaleIcon(create(color))

  private const val iconSize = 14

  private val arcSize
    get() = if (ExperimentalUI.isNewUI()) 4 else 0
}