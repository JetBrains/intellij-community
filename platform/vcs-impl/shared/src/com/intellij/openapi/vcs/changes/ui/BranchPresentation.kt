// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import java.awt.Color
import javax.swing.UIManager

object BranchPresentation {
  @JvmField
  val TEXT_COLOR: JBColor = namedColor("VersionControl.RefLabel.foreground", JBColor(Color(0x7a7a7a), Color(0x909090)))

  private val BACKGROUND_BASE_COLOR = namedColor("VersionControl.RefLabel.backgroundBase", JBColor(Color.BLACK, Color.WHITE))

  private val BACKGROUND_BALANCE
    get() = namedDouble("VersionControl.RefLabel.backgroundBrightness", 0.08)

  @JvmStatic
  fun getBranchPresentationBackground(background: Color): Color =
    ColorUtil.mix(background, BACKGROUND_BASE_COLOR, BACKGROUND_BALANCE)

  @Suppress("SameParameterValue")
  private fun namedDouble(name: String, default: Double): Double {
    return when (val value = UIManager.get(name)) {
      is Double -> value
      is Int -> value.toDouble()
      is String -> value.toDoubleOrNull() ?: default
      else -> default
    }
  }
}