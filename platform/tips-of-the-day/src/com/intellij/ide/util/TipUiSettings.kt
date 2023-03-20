// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.border.Border

internal object TipUiSettings {
  @JvmStatic
  val imageMaxWidth: Int
    get() = JBUI.scale(498)
  @JvmStatic
  val tipPanelMinHeight: Int
    get() = JBUI.scale(190)
  @JvmStatic
  val tipPanelMaxHeight: Int
    get() = JBUI.scale(540)
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
    get() = JBUI.scale(8)
  @JvmStatic
  val feedbackPanelTopIndent: Int
    get() = JBUI.scale(8)
  @JvmStatic
  val feedbackIconIndent: Int
    get() = JBUI.scale(6)
  @JvmStatic
  val tipPanelBorder: Border
    get() = BorderFactory.createEmptyBorder(tipPanelTopIndent, tipPanelLeftIndent, tipPanelBottomIndent, tipPanelRightIndent)
  @JvmStatic
  val imageBorderColor: Color
    get() = JBColor.namedColor("TipOfTheDay.Image.borderColor", JBColor.border())
  @JvmStatic
  val panelBackground: Color
    get() = JBColor.namedColor("TextField.background", 0xFFFFFF, 0x2B2D30)
}