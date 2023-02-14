// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI

open class InteractiveCoursePanel(protected val data: InteractiveCourseData, private val contentEnabled: Boolean = true) : JPanel() {

  val startLearningButton = JButton()

  // needed to align panel with button border without selection
  protected val leftMargin = 3

  private val newContentMarker = data.newContentMarker()
  private val nameLine: JPanel? = if (data.newContentMarker() != null) JPanel() else null

  private val interactiveCourseDescription = HeightLimitedPane(data.getDescription(), -1, LearnIdeContentColorsAndFonts.HeaderColor)

  private val calculateInnerComponentHeight: () -> Int = { preferredSize.height }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    alignmentY = TOP_ALIGNMENT

    val headerPanel = createHeaderPanel()
    headerPanel.border = JBUI.Borders.emptyLeft(leftMargin)
    headerPanel.isEnabled = contentEnabled
    this.add(headerPanel)

    interactiveCourseDescription.apply {
      border = JBUI.Borders.empty(5, leftMargin, 14, 0)
      isEnabled = contentEnabled
    }
    this.add(interactiveCourseDescription)
    this.add(this.createSouthPanel().also { it.alignmentX = LEFT_ALIGNMENT })
  }

  override fun getMaximumSize(): Dimension {
    return Dimension(this.preferredSize.width, calculateInnerComponentHeight())
  }

  private fun createHeaderPanel(): JComponent {
    val learnIdeFeaturesHeader = DynamicFontLabel(data.getName(), data.getIcon()).apply {
      val labelFont = StartupUiUtil.getLabelFont()
      font = labelFont.deriveFont(Font.BOLD).deriveFont(labelFont.size2D + if (SystemInfo.isWindows) JBUIScale.scale(1) else 0 )
    }

    return if (nameLine != null) {
      nameLine.isOpaque = false
      nameLine.layout = BoxLayout(nameLine, BoxLayout.X_AXIS)
      nameLine.alignmentX = LEFT_ALIGNMENT

      nameLine.add(learnIdeFeaturesHeader)
      nameLine.add(rigid())
      nameLine.add(newContentMarker)

      nameLine
    }
    else {
      learnIdeFeaturesHeader
    }
  }

  protected open fun createSouthPanel() = createButtonPanel(data.getAction())

  protected fun createButtonPanel(action: Action): JPanel {
    startLearningButton.action = action
    startLearningButton.margin = JBUI.emptyInsets()
    startLearningButton.isOpaque = false
    startLearningButton.isContentAreaFilled = false
    startLearningButton.isEnabled = contentEnabled

    return BorderLayoutPanel().apply {
      isOpaque = false
      addToLeft(startLearningButton)
    }
  }

  private fun rigid(): Component {
    return Box.createRigidArea(
      Dimension(JBUI.scale(10), JBUI.scale(0))).apply { (this as JComponent).alignmentX = LEFT_ALIGNMENT }
  }

  class DynamicFontLabel(@Nls text: String, icon: Icon? = null): JBLabel(text, icon, SwingConstants.LEFT) {

    override fun setUI(ui: LabelUI?) {
      super.setUI(ui)
      if (font != null) {
        font = FontUIResource(font.deriveFont(
          StartupUiUtil.getLabelFont().size.toFloat() + if (SystemInfo.isWindows) JBUIScale.scale(1) else 0 ).deriveFont(Font.BOLD))
      }
    }
  }

}