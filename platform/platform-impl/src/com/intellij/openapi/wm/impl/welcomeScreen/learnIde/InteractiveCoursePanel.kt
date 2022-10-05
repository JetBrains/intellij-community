// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.*
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI

open class InteractiveCoursePanel(protected val data: InteractiveCourseData) : JPanel() {

  val startLearningButton = JButton()

  private val newContentMarker = data.newContentMarker()
  private val nameLine: JPanel? = if (data.newContentMarker() != null) JPanel() else null

  private val interactiveCourseDescription = HeightLimitedPane(data.getDescription(), -1, LearnIdeContentColorsAndFonts.HeaderColor)
  private val interactiveCourseContent: JPanel

  private val calculateInnerComponentHeight: () -> Int = { preferredSize.height }

  init {
    layout = BoxLayout(this, BoxLayout.LINE_AXIS)
    isOpaque = false
    alignmentX = LEFT_ALIGNMENT

    interactiveCourseContent = createInteractiveCourseContent()
    this.add(interactiveCourseContent)
  }

  override fun getMaximumSize(): Dimension {
    return Dimension(this.preferredSize.width, calculateInnerComponentHeight())
  }

  private fun createInteractiveCourseContent(): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)
    panel.isOpaque = false
    panel.alignmentY = TOP_ALIGNMENT

    panel.add(rigid(12, 10))

    val learnIdeFeaturesHeader = DynamicFontLabel(data.getName(), data.getIcon())

    learnIdeFeaturesHeader.apply { val labelFont = StartupUiUtil.getLabelFont()
      font = labelFont.deriveFont(Font.BOLD).deriveFont(labelFont.size2D + if (SystemInfo.isWindows) JBUIScale.scale(1) else 0 )
    }
    learnIdeFeaturesHeader.alignmentX = LEFT_ALIGNMENT

    if (nameLine != null) {
      nameLine.isOpaque = false
      nameLine.layout = BoxLayout(nameLine, BoxLayout.X_AXIS)
      nameLine.alignmentX = LEFT_ALIGNMENT

      nameLine.add(learnIdeFeaturesHeader)
      nameLine.add(rigid(10, 0))
      nameLine.add(newContentMarker)

      panel.add(nameLine)
    } else {
      panel.add(learnIdeFeaturesHeader)
    }

    panel.add(rigid(1, 4))
    panel.add(interactiveCourseDescription)
    panel.add(rigid(4, 9))

    panel.add(this.createSouthPanel())

    panel.add(rigid(18, 21))

    return panel
  }

  protected open fun createSouthPanel() = createButtonPanel(data.getAction())

  protected fun createButtonPanel(action: Action): JPanel {
    startLearningButton.action = action
    startLearningButton.margin = Insets(0, 0, 0, 0)
    startLearningButton.isOpaque = false
    startLearningButton.alignmentX = LEFT_ALIGNMENT

    return buttonPixelHunting(startLearningButton)
  }

  private fun buttonPixelHunting(button: JButton): JPanel {

    val buttonSizeWithoutInsets = Dimension(button.preferredSize.width - button.insets.left - button.insets.right,
                                            button.preferredSize.height - button.insets.top - button.insets.bottom)

    val buttonPlace = JPanel().apply {
      layout = null
      maximumSize = buttonSizeWithoutInsets
      preferredSize = buttonSizeWithoutInsets
      minimumSize = buttonSizeWithoutInsets
      isOpaque = false
      alignmentX = LEFT_ALIGNMENT
    }

    buttonPlace.add(button)
    button.bounds = Rectangle(-button.insets.left, -button.insets.top, button.preferredSize.width, button.preferredSize.height)

    return buttonPlace
  }


  private fun rigid(_width: Int, _height: Int): Component {
    return Box.createRigidArea(
      Dimension(JBUI.scale(_width), JBUI.scale(_height))).apply { (this as JComponent).alignmentX = LEFT_ALIGNMENT }
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