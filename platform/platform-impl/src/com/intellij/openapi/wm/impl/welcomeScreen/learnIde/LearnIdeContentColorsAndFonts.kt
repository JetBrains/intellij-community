// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

object LearnIdeContentColorsAndFonts {

  val ActiveInteractiveCoursesBorder = JBUI.CurrentTheme.Component.FOCUSED_BORDER_COLOR
  val InactiveInteractiveCoursesBorder = JBColor.namedColor("Component.borderColor", JBColor(0xC4C4C4, 0x5E6060))
  val HeaderColor = JBColor.namedColor("ParameterInfo.foreground", JBColor(0x1D1D1D, 0xBBBBBB))
  val HoveredColor = JBColor.namedColor("Plugins.lightSelectionBackground", JBColor(0xEDF6FE, 0x464A4D))
  val ModuleHeaderColor = JBColor.namedColor("link", JBColor(0x2470B3, 0x589DF6))
  val ModuleDescriptionColor = JBColor.namedColor("infoPanelForeground", JBColor(0x808080, 0x8C8C8C))

  val PARAGRAPH_STYLE = SimpleAttributeSet()

  init {
    applyZeroParagraphStyle(PARAGRAPH_STYLE)
  }

  private fun applyZeroParagraphStyle(simpleAttributeSet: SimpleAttributeSet) {
    StyleConstants.setLeftIndent(simpleAttributeSet, 0.0f)
    StyleConstants.setRightIndent(simpleAttributeSet, 0f)
    StyleConstants.setSpaceAbove(simpleAttributeSet, 0.0f)
    StyleConstants.setSpaceBelow(simpleAttributeSet, 0.0f)
    StyleConstants.setLineSpacing(simpleAttributeSet, 0.0f)
  }

}