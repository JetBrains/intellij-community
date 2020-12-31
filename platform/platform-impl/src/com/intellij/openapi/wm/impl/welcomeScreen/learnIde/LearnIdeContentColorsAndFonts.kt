// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.ide.ui.UISettings
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import java.awt.Font
import javax.swing.JLabel
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

object LearnIdeContentColorsAndFonts {

  val InteractiveCoursesBorder = JBColor.namedColor("Button.startBorderColor", JBColor(0x87AFDA, 0x5E6060))

  private val settings: UISettings by lazy { UISettings.instance }
  private val fontSize: Int by lazy { if (settings.overrideLafFonts) settings.fontSize else JBFont.label().size }
  private val fontFace: String by lazy { settings.fontFace ?: JLabel().font.fontName }

  val HeaderFont: Font by lazy { Font(fontFace, Font.BOLD, fontSize + 5) }
  val HeaderColor = JBColor.namedColor("ParameterInfo.foreground", JBColor(0x1D1D1D, 0xBBBBBB))


  val HoveredColor = JBColor.namedColor("Plugins.lightSelectionBackground", JBColor(0xEDF6FE, 0x464A4D))
  val REGULAR = SimpleAttributeSet()

  val ModuleHeaderColor = JBColor.namedColor("link", JBColor(0x2470B3, 0x589DF6))
  val ModuleDescriptionColor = JBColor.namedColor("disabledText", JBColor(0x8C8C8C, 0x777777))
  val MODULE_HEADER = SimpleAttributeSet()
  val MODULE_DESCRIPTION = SimpleAttributeSet()

  val PARAGRAPH_STYLE = SimpleAttributeSet()
  val HEADER = SimpleAttributeSet()

  init {
    StyleConstants.setFontSize(REGULAR, UISettings.instance.fontSize - 1)
    StyleConstants.setFontFamily(REGULAR, UISettings.instance.fontFace)
    StyleConstants.setForeground(REGULAR, HeaderColor)

    StyleConstants.setLeftIndent(REGULAR, 0.0f)
    StyleConstants.setRightIndent(REGULAR, 0f)
    StyleConstants.setSpaceAbove(REGULAR, 0.0f)
    StyleConstants.setSpaceBelow(REGULAR, 0.0f)
    StyleConstants.setLineSpacing(REGULAR, 0.0f)

    StyleConstants.setFontSize(HEADER, HeaderFont.size)
    StyleConstants.setFontFamily(HEADER, UISettings.instance.fontFace)
    StyleConstants.setForeground(HEADER, HeaderColor)
    StyleConstants.setBold(HEADER, true)

    StyleConstants.setLeftIndent(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setRightIndent(PARAGRAPH_STYLE, 0f)
    StyleConstants.setSpaceAbove(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setSpaceBelow(PARAGRAPH_STYLE, 0.0f)
    StyleConstants.setLineSpacing(PARAGRAPH_STYLE, 0.0f)

    StyleConstants.setFontFamily(MODULE_HEADER, UISettings.instance.fontFace)
    StyleConstants.setFontSize(MODULE_HEADER, UISettings.instance.fontSize - 1)
    StyleConstants.setForeground(MODULE_HEADER, ModuleHeaderColor)
    StyleConstants.setLeftIndent(MODULE_HEADER, 0.0f)
    StyleConstants.setRightIndent(MODULE_HEADER, 0f)
    StyleConstants.setSpaceAbove(MODULE_HEADER, 0.0f)
    StyleConstants.setSpaceBelow(MODULE_HEADER, 0.0f)
    StyleConstants.setLineSpacing(MODULE_HEADER, 0.0f)

    StyleConstants.setFontFamily(MODULE_DESCRIPTION, UISettings.instance.fontFace)
    StyleConstants.setFontSize(MODULE_DESCRIPTION, UISettings.instance.fontSize - 2)
    StyleConstants.setForeground(MODULE_DESCRIPTION, ModuleDescriptionColor)
    StyleConstants.setLeftIndent(MODULE_DESCRIPTION, 0.0f)
    StyleConstants.setRightIndent(MODULE_DESCRIPTION, 0f)
    StyleConstants.setSpaceAbove(MODULE_DESCRIPTION, 0.0f)
    StyleConstants.setSpaceBelow(MODULE_DESCRIPTION, 0.0f)
    StyleConstants.setLineSpacing(MODULE_DESCRIPTION, 0.0f)

  }

}