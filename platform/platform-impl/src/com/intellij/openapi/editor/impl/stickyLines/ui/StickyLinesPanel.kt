// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLine
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLines
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBPanel
import javax.swing.BoxLayout


internal class StickyLinesPanel(
  private val editor: EditorEx,
  private val visualStickyLines: VisualStickyLines,
  shadowedBorder: StickyLineShadowBorder,
) : JBPanel<StickyLinesPanel>() {

  private val layeredPane: JBLayeredPane = JBLayeredPane()
  private val stickyComponents: StickyLineComponents = StickyLineComponents(editor, layeredPane)

  private var panelW: Int = 0
  private var panelH: Int = 0

  init {
    isOpaque = false
    border = shadowedBorder
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    layeredPane.layout = null
    add(layeredPane)
  }

  // ------------------------------------------- API -------------------------------------------

  fun repaintLines(startVisualLine: Int, endVisualLine: Int) {
    if (isPanelEnabled()) {
      for (lineComp: StickyLineComponent in stickyComponents.components()) {
        lineComp.repaintIfInRange(startVisualLine, endVisualLine)
      }
    }
  }

  fun repaintLines() {
    if (isPanelEnabled()) {
      repaintLinesImpl()
    }
  }

  fun startDumb() {
    if (isPanelEnabled()) {
      for (lineComp: StickyLineComponent in stickyComponents.components()) {
        lineComp.startDumb()
      }
    }
  }

  // ------------------------------------------- Impl -------------------------------------------

  private fun repaintLinesImpl() {
    val panelWidth: Int = stickyLinesPanelWidth()
    val lineHeight: Int = editor.lineHeight
    var index = 0
    val components: Iterator<StickyLineComponent> = stickyComponents.unboundComponents().iterator()
    val stickyLines: List<VisualStickyLine> = visualStickyLines.lines(editor.scrollingModel.visibleArea)
    for (stickyLine: VisualStickyLine in stickyLines) {
      val component: StickyLineComponent = components.next()
      component.setLine(
        stickyLine.primaryLine(),
        stickyLine.scopeLine(),
        stickyLine.navigateOffset(),
        stickyLine.debugText(),
      )
      component.setBounds(0, stickyLine.yLocation, panelWidth, lineHeight)
      component.isVisible = true
      index++
    }
    stickyComponents.resetAfterIndex(index)
    val panelHeight: Int = visualStickyLines.height()
    if (isPanelSizeChanged(panelWidth, panelHeight)) {
      this.panelW = panelWidth
      this.panelH = panelHeight
      setSize(panelWidth, shadowedBorderHeight())
      layeredPane.setSize(panelWidth, panelHeight)
      revalidate()
    }
    repaint()
  }

  // ------------------------------------------- Utils -------------------------------------------

  private fun shadowedBorderHeight(): Int {
    return if (panelH == 0) 0 else panelH + (border as StickyLineShadowBorder).borderHeight()
  }

  private fun isPanelSizeChanged(panelWidth: Int, panelHeight: Int): Boolean {
    return this.panelW != panelWidth || this.panelH != panelHeight
  }

  private fun stickyLinesPanelWidth(): Int {
    return (editor as EditorImpl).stickyLinesPanelWidth
  }

  private fun isPanelEnabled(): Boolean {
    val isEnabled = editor.settings.areStickyLinesShown()
    if (!isEnabled && stickyComponents.clear()) {
      panelW = 0
      panelH = 0
      layeredPane.setSize(0, 0)
      setSize(0, 0)
      revalidate()
      repaint()
    }
    return isEnabled
  }
}
