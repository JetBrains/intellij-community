// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.highlighter

import com.intellij.cce.report.ReportColors
import java.awt.Color

object HighlightColors : ReportColors<Color> {
  override val perfectSortingColor = Color(188, 245, 188)
  override val goodSortingColor = Color(255, 250, 205)
  override val badSortingColor = Color(255, 200, 127)
  override val notFoundColor = Color(255, 153, 153)
  override val absentLookupColor = Color(112, 170, 255)
  override val goodSortingThreshold = 5
}