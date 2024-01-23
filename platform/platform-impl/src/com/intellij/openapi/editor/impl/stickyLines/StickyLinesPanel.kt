// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.ClientProperty
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBPanel
import java.awt.Color
import javax.swing.BoxLayout
import kotlin.math.min

internal class StickyLinesPanel(private val editor: EditorEx) : JBPanel<StickyLinesPanel>() {

  // ui
  private val layeredPane: JBLayeredPane = JBLayeredPane()

  // ui + state
  private val stickyLinesComp: MutableList<StickyLineComponent> = mutableListOf()

  // state
  private val stickyLines: MutableList<StickyLine> = mutableListOf()
  private var editorY: Int = 0
  private var editorH: Int = 0
  private var panelW: Int = 0
  private var panelH: Int = 0

  // settings
  private val scopeThreshold: Int = 3
  private val lineLimit: Int get() = editor.settings.stickyLinesLimit
  val isStickyEnabled: Boolean get() = editor.settings.isStickyLinesShown

  init {
    border = bottomBorder()
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    layeredPane.layout = null
    add(layeredPane)
  }

  // ------------------------------------------- API -------------------------------------------

  fun repaintLinesInRange(startVisualLine: Int, endVisualLine: Int) {
    if (clearPanelIfDisabled()) return
    for (lineComp: StickyLineComponent in stickyLinesComp) {
      if (lineComp.isEmpty()) {
        break
      }
      if (lineComp.primaryVisualLine in startVisualLine..endVisualLine) {
        lineComp.repaint()
      }
    }
  }

  fun repaintLines(newEditorY: Int, newEditorH: Int) {
    if (clearPanelIfDisabled()) return
    editorY = newEditorY
    editorH = newEditorH
    repaintLinesImpl()
  }

  fun repaintLines(newEditorY: Int, newEditorH: Int, newStickyLines: Collection<StickyLine>) {
    if (clearPanelIfDisabled()) return
    editorY = newEditorY
    editorH = newEditorH
    stickyLines.clear()
    stickyLines.addAll(newStickyLines)
    repaintLinesImpl()
  }

  fun repaintLines(newStickyLines: Collection<StickyLine>) {
    if (clearPanelIfDisabled()) return
    stickyLines.clear()
    stickyLines.addAll(newStickyLines)
    repaintLinesImpl()
  }

  fun suppressHintForLine(logicalLine: Int): Boolean {
    for (lineComp: StickyLineComponent in stickyLinesComp) {
      if (lineComp.isEmpty()) {
        break
      }
      val stickyVisualPos = VisualPosition(lineComp.primaryVisualLine, 0)
      val stickyLogicalLine: Int = editor.visualToLogicalPosition(stickyVisualPos).line
      if (logicalLine == stickyLogicalLine) {
        return true
      }
      if (logicalLine == stickyLogicalLine - 1) {
        // case when lineOf(psiElement.startOffset) != lineOf(psiElement.textOffset)
        // e.g., when a method starts with @Override
        // "stickyLogicalLine - 1" won't cover all the cases, but the fair approach looks difficult to implement
        return true
      }
    }
    return false
  }

  // ------------------------------------------- Impl -------------------------------------------

  private fun repaintLinesImpl() {
    val lineLimit: Int = lineLimit // avoid reading settings several times while painting
    val stickyLines: List<StickyLine> = getStickyLines()
    lazyAllocateComponents(stickyLines, lineLimit)
    updateComponentsState(stickyLines, lineLimit)
    placeComponentsOnPanel()
  }

  private fun getStickyLines(): List<StickyLine> {
    return stickyLines
      // TODO: why are range markers not valid even if the document is not changed?
      // .filter { it.getRangeMarker().isValid }
  }

  private fun lazyAllocateComponents(stickyLines: List<StickyLine>, lineLimit: Int) {
    while (stickyLinesComp.size < min(stickyLines.size, lineLimit)) {
      val lineComp = StickyLineComponent(editor)
      layeredPane.add(lineComp, (200 - stickyLinesComp.size) as Any)
      stickyLinesComp.add(lineComp)
    }
  }

  private fun updateComponentsState(stickyLines: List<StickyLine>, lineLimit: Int) {
    var compIndex = 0
    for (stickyLine: StickyLine in stickyLines) {
      val primaryVisual: VisualPosition = toVisualPos(stickyLine.primaryLine())
      val scopeVisual: VisualPosition = toVisualPos(stickyLine.scopeLine())
      if (isScopeNotNarrow(primaryVisual, scopeVisual) && isLineNotDuplicate(compIndex, primaryVisual)) {
        stickyLinesComp[compIndex].setLine(primaryVisual, scopeVisual, stickyLine.navigateOffset())
        compIndex++
        if (compIndex == lineLimit) {
          break
        }
      }
    }
    while (compIndex < stickyLinesComp.size) {
      stickyLinesComp[compIndex].resetLine()
      compIndex++
    }
  }

  private fun placeComponentsOnPanel() {
    val lineHeight: Int = editor.lineHeight
    var panelHeight = 0
    val panelWidth = (editor as EditorImpl).stickyLinesPanelWidth
    for (lineComp: StickyLineComponent in stickyLinesComp) {
      if (lineComp.isEmpty() || isPanelTooBig(panelHeight, lineHeight)) {
        lineComp.isVisible = false
      } else {
        val addedHeight: Int = placeComponentOnPanel(lineComp, panelWidth, panelHeight, lineHeight)
        // 0 <= addedHeight <= lineHeight
        panelHeight += addedHeight
      }
    }
    if (isPanelSizeChanged(panelWidth, panelHeight)) {
      setSize(panelWidth, if (panelHeight == 0) 0 else panelHeight + /*border*/ 1)
      this.panelW = panelWidth
      this.panelH = panelHeight
      layeredPane.setSize(panelWidth, panelHeight)
      revalidate()
    }
    repaint()
  }

  private fun placeComponentOnPanel(
    lineComp: StickyLineComponent,
    panelWidth: Int,
    panelHeight: Int,
    lineHeight: Int,
  ): Int {
    val startY1: Int = editor.visualLineToY(lineComp.primaryVisualLine)
    val startY2: Int = startY1 + lineHeight
    val endY1: Int = editor.visualLineToY(lineComp.scopeVisualLine)
    val endY2: Int = endY1 + lineHeight

    val stickyY: Int = editorY + panelHeight + lineHeight
    if (stickyY in startY2..endY2) {
      val lineYShift = if (stickyY <= endY1) 0 else stickyY - endY1
      lineComp.isVisible = true
      lineComp.setBounds(0, panelHeight - lineYShift, panelWidth, lineHeight)
      return lineHeight - lineYShift
    } else {
      return 0
    }
  }

  // ------------------------------------------- Utils -------------------------------------------

  private fun toVisualPos(logicalLine: Int): VisualPosition {
    return editor.logicalToVisualPosition(LogicalPosition(logicalLine, 0))
  }

  private fun isScopeNotNarrow(primaryVisual: VisualPosition, scopeVisual: VisualPosition): Boolean {
    return scopeVisual.line - primaryVisual.line + 1 >= scopeThreshold
  }

  private fun isLineNotDuplicate(compIndex: Int, primaryVisual: VisualPosition): Boolean {
    if (compIndex == 0) return true
    val visualLine: Int = primaryVisual.line
    val prevVisualLine = stickyLinesComp[compIndex - 1].primaryVisualLine
    assert(prevVisualLine <= visualLine) {
      "sticky lines list sorted not properly, wrong order of visual lines: $prevVisualLine, $visualLine"
    }
    return prevVisualLine != visualLine
  }

  private fun isPanelTooBig(panelHeight: Int, lineHeight: Int): Boolean {
    return panelHeight + 2 * lineHeight > editorH / 2
  }

  private fun isPanelSizeChanged(panelWidth: Int, panelHeight: Int): Boolean {
    return this.panelW != panelWidth || this.panelH != panelHeight
  }

  private fun clearPanelIfDisabled(): Boolean {
    val isEnabled = isStickyEnabled
    if (!isEnabled && stickyLinesComp.isNotEmpty()) {
      layeredPane.removeAll()
      stickyLinesComp.clear()
      stickyLines.clear()
      editorY = 0
      editorH = 0
      panelW = 0
      panelH = 0
      setSize(0, 0)
      revalidate()
      repaint()
    }
    return !isEnabled
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
        val scheme = EditorColorsManager.getInstance().globalScheme
        return scheme.getColor(EditorColors.TEARLINE_COLOR) ?: scheme.defaultBackground
      }
    }
  }
}
