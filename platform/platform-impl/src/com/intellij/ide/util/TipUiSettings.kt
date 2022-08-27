// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.util.ui.JBUI
import javax.swing.BorderFactory
import javax.swing.border.Border

internal object TipUiSettings {
  const val imageWidth = 498
  const val imageHeight = 248

  @JvmStatic
  val tipPanelLeftIndent: Int
    get() = JBUI.scale(24)
  @JvmStatic
  val tipPanelRightIndent: Int
    get() = JBUI.scale(24)
  @JvmStatic
  val tipPanelTopIndent: Int
    get() = JBUI.scale(16)
  @JvmStatic
  val tipPanelBottomIndent: Int
    get() = JBUI.scale(2)
  @JvmStatic
  val feedbackPanelTopIndent: Int
    get() = JBUI.scale(12)
  @JvmStatic
  val feedbackIconIndent: Int
    get() = JBUI.scale(6)
  @JvmStatic
  val tipPanelBorder: Border
    get() = BorderFactory.createEmptyBorder(tipPanelTopIndent, tipPanelLeftIndent, tipPanelBottomIndent, tipPanelRightIndent)
}