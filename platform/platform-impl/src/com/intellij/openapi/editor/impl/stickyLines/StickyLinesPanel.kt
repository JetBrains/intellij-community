// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.ClientProperty
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBPanel
import java.awt.Color
import java.awt.Graphics
import javax.swing.BoxLayout

internal class StickyLinesPanel(
  private val editor: EditorEx,
  private val shadowPainter: StickyLineShadowPainter,
  private val visualStickyLines: VisualStickyLines,
) : JBPanel<StickyLinesPanel>() {

  private val layeredPane: JBLayeredPane = JBLayeredPane()
  private val stickyComponents: StickyLineComponents = StickyLineComponents(editor, layeredPane)

  private var panelW: Int = 0
  private var panelH: Int = 0

  init {
    border = bottomBorder()
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
    val panelWidth: Int = (editor as EditorImpl).stickyLinesPanelWidth
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
      setSize(panelWidth, if (panelHeight == 0) 0 else panelHeight + /*border*/ 1)
      this.panelW = panelWidth
      this.panelH = panelHeight
      layeredPane.setSize(panelWidth, panelHeight)
      revalidate()
    }
    repaint()
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    super.setBounds(x, y, width, height)
    shadowPainter.updateShadow(width, height, editor.lineHeight)
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    shadowPainter.paintShadow(g)
  }

  // ------------------------------------------- Utils -------------------------------------------

  private fun isPanelSizeChanged(panelWidth: Int, panelHeight: Int): Boolean {
    return this.panelW != panelWidth || this.panelH != panelHeight
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

  private fun bottomBorder(): SideBorder {
    return object : SideBorder(null, BOTTOM) {
      override fun getLineColor(): Color {
        val borderColor = ClientProperty.get(
          this@StickyLinesPanel,
          FileEditorManager.SEPARATOR_COLOR
        )
        if (borderColor != null) {
          return borderColor
        }
        val scheme = editor.getColorsScheme()
        return scheme.getColor(EditorColors.RIGHT_MARGIN_COLOR) ?: scheme.defaultBackground
      }
    }
  }
}
