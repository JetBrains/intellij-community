// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI

class InteractiveCoursePanel(private val data: InteractiveCourseData) : JPanel() {

  private enum class ContentState { COLLAPSED, EXPANDED }

  private val pluginPanelWidth = 72
  private val chevronPanelWidth = 55

  val startLearningButton = JButton()

  private val newContentMarker = data.newContentMarker()
  private val nameLine: JPanel? = if (data.newContentMarker() != null) JPanel() else null

  private val interactiveCourseDescription = HeightLimitedPane(data.getDescription(), -1, LearnIdeContentColorsAndFonts.HeaderColor)
  private val interactiveCourseContent = createInteractiveCourseContent()

  private var contentState: ContentState = ContentState.COLLAPSED
  private val expandCollapseListener: MouseListener = createExpandCollapseListener()

  private val expandedCourseContent: JComponent by lazy { data.getExpandContent() }
  private val chevronPanel = JPanel()
  private val chevronLabel = JLabel(AllIcons.General.ChevronDown)
  val pluginPanel = JPanel()
  val pluginLabel = JLabel(data.getIcon())

  private val roundBorder1pxActive = CompoundBorder(RoundedLineBorder(LearnIdeContentColorsAndFonts.ActiveInteractiveCoursesBorder, 8, 1), JBUI.Borders.emptyRight(5))

  private val roundBorder1pxInactive = CompoundBorder(RoundedLineBorder(LearnIdeContentColorsAndFonts.InactiveInteractiveCoursesBorder, 8, 1), JBUI.Borders.emptyRight(5))

  private val calculateInnerComponentHeight: () -> Int = { preferredSize.height }

  init {
    initPluginPanel()
    initChevronPanel()

    layout = BoxLayout(this, BoxLayout.LINE_AXIS)
    isOpaque = false
    border = roundBorder1pxInactive
    alignmentX = LEFT_ALIGNMENT

    add(pluginPanel)
    add(interactiveCourseContent)
    add(chevronPanel)

    addMouseListener(expandCollapseListener)
    interactiveCourseDescription.addMouseListener(expandCollapseListener)
  }

  private fun initChevronPanel() {
    chevronPanel.apply {
      layout = BorderLayout()
      isOpaque = false
      alignmentY = TOP_ALIGNMENT
      add(chevronLabel, BorderLayout.CENTER)
      add(createUnshrinkablePanel(chevronPanelWidth), BorderLayout.NORTH)
    }
  }

  private fun initPluginPanel() {
    pluginPanel.apply {
      layout = BorderLayout()
      border = EmptyBorder(12, 0, 0, 0)
      isOpaque = false
      add(pluginLabel, BorderLayout.NORTH)
      alignmentY = TOP_ALIGNMENT
      add(createUnshrinkablePanel(pluginPanelWidth), BorderLayout.CENTER)
    }
  }

  private fun createUnshrinkablePanel(_width: Int): JPanel {
    return object: JPanel() {
      init {
        preferredSize = Dimension(JBUIScale.scale(_width), 1)
        minimumSize = Dimension(JBUIScale.scale(_width), 1)
        isOpaque = false
      }

      override fun updateUI() {
        super.updateUI()
        preferredSize = Dimension(JBUIScale.scale(_width), 1)
        minimumSize = Dimension(JBUIScale.scale(_width), 1)
        isOpaque = false
      }
    }
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

    val learnIdeFeaturesHeader = DynamicFontLabel(data.getName())

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

    startLearningButton.action = data.getAction()
    startLearningButton.margin = Insets(0, 0, 0, 0)
    startLearningButton.isSelected = true
    startLearningButton.isOpaque = false
    startLearningButton.alignmentX = LEFT_ALIGNMENT

    val buttonPlace = buttonPixelHunting(startLearningButton)
    panel.add(buttonPlace)

    panel.add(rigid(18, 21))

    return panel
  }

  private fun createExpandCollapseListener(): MouseAdapter {
    return object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        if (contentState == ContentState.EXPANDED) return
        activateLearnIdeFeaturesPanel()
      }

      override fun mouseExited(e: MouseEvent?) {
        if (e == null) return
        deactivateLearnIdeFeaturesPanel(e.locationOnScreen)
      }

      override fun mousePressed(e: MouseEvent?) {
        onLearnIdeFeaturesPanelClick()
      }
    }
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

  private fun activateLearnIdeFeaturesPanel() {
    background = LearnIdeContentColorsAndFonts.HoveredColor
    isOpaque = true
    border = roundBorder1pxActive
    repaint()
    cursor = Cursor(Cursor.HAND_CURSOR)
  }

  private fun deactivateLearnIdeFeaturesPanel(mouseLocationOnScreen: Point) {
    if (pointerInLearnIdeFeaturesPanel(mouseLocationOnScreen)) return
    isOpaque = false
    border = roundBorder1pxInactive
    repaint()
    cursor = Cursor(Cursor.DEFAULT_CURSOR)
  }

  private fun onLearnIdeFeaturesPanelClick() {
    if (contentState == ContentState.COLLAPSED) {
      expandContent()
      chevronLabel.icon = AllIcons.General.ChevronUp
      contentState = ContentState.EXPANDED
    }
    else {
      collapseContent()
      chevronLabel.icon = AllIcons.General.ChevronDown
      contentState = ContentState.COLLAPSED
    }
    chevronLabel.repaint()
  }

  private fun expandContent() {
    this.isOpaque = false
    interactiveCourseDescription.maximumSize = interactiveCourseDescription.preferredSize
    interactiveCourseContent.add(expandedCourseContent)
    nameLine?.remove(newContentMarker)
    chevronPanel.maximumSize = chevronPanel.size
    interactiveCourseContent.revalidate()
    interactiveCourseContent.repaint()
    border = roundBorder1pxInactive
    repaint()

  }

  private fun collapseContent() {
    val pointerLocation = MouseInfo.getPointerInfo().location
    if (pointerInLearnIdeFeaturesPanel(pointerLocation)) activateLearnIdeFeaturesPanel()
    interactiveCourseContent.remove(expandedCourseContent)
    nameLine?.add(newContentMarker)
    interactiveCourseContent.revalidate()
    interactiveCourseContent.repaint()
    repaint()
  }

  private fun pointerInLearnIdeFeaturesPanel(mouseLocationOnScreen: Point) =
    Rectangle(Point(locationOnScreen.x + 1, locationOnScreen.y + 1),
              Dimension(bounds.size.width - 2, bounds.size.height - 2)).contains(
      mouseLocationOnScreen)


  private fun rigid(_width: Int, _height: Int): Component {
    return Box.createRigidArea(
      Dimension(JBUI.scale(_width), JBUI.scale(_height))).apply { (this as JComponent).alignmentX = LEFT_ALIGNMENT }
  }

  class DynamicFontLabel(@Nls text: String): JBLabel(text) {

    override fun setUI(ui: LabelUI?) {
      super.setUI(ui)
      if (font != null) {
        font = FontUIResource(font.deriveFont(
          StartupUiUtil.getLabelFont().size.toFloat() + if (SystemInfo.isWindows) JBUIScale.scale(1) else 0 ).deriveFont(Font.BOLD))
      }
    }
  }

}