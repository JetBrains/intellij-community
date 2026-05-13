// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal class AgentSessionsArchivedContextHeaderComponent(
  private val action: AnAction?,
  private val showChevron: Boolean,
  bold: Boolean,
) : JComponent() {
  private var label: String = ""

  init {
    isOpaque = false
    cursor = if (action != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    font = if (bold) JBFont.label().asBold() else JBFont.label()
    if (action != null) {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          if (e.button == MouseEvent.BUTTON1 && isEnabled) performAction(e)
        }
      })
    }
  }

  fun update(presentation: Presentation) {
    val newLabel = presentation.text.orEmpty()
    val changed = newLabel != label
    label = newLabel
    isEnabled = presentation.isEnabled
    isVisible = presentation.isVisible
    cursor = if (action != null && isEnabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    @Suppress("UseHtmlChunkToolTip") // plain Nls description from a properties bundle
    run { toolTipText = presentation.description }
    if (changed) revalidate()
    repaint()
  }

  override fun getPreferredSize(): Dimension {
    val metrics = getFontMetrics(font)
    val padding = JBUI.scale(HORIZONTAL_PADDING)
    val textWidth = metrics.stringWidth(label)
    val chevronWidth = if (showChevron) JBUI.scale(CHEVRON_WIDTH) else 0
    val gap = if (showChevron) JBUI.scale(TEXT_CHEVRON_GAP) else 0
    return Dimension(padding * 2 + textWidth + gap + chevronWidth, JBUI.scale(HEIGHT))
  }

  override fun getMinimumSize(): Dimension = preferredSize

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      val h = height
      val padding = JBUI.scale(HORIZONTAL_PADDING)
      g2.font = font
      val metrics = g2.fontMetrics
      val textY = (h - metrics.height) / 2 + metrics.ascent
      g2.color = if (isEnabled) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
      g2.drawString(label, padding, textY)

      if (!showChevron) return

      val chevronX = padding + metrics.stringWidth(label) + JBUI.scale(TEXT_CHEVRON_GAP)
      val chevronW = JBUI.scale(CHEVRON_WIDTH)
      val chevronH = JBUI.scale(CHEVRON_HEIGHT)
      val chevronY = (h - chevronH) / 2
      val midX = chevronX + chevronW / 2
      val xs = intArrayOf(chevronX, chevronX + chevronW, midX)
      val ys = intArrayOf(chevronY, chevronY, chevronY + chevronH)
      g2.fillPolygon(xs, ys, 3)
    }
    finally {
      g2.dispose()
    }
  }

  private fun performAction(inputEvent: MouseEvent) {
    val action = action ?: return
    val dataContext = ActionToolbar.getDataContextFor(this)
    val event = AnActionEvent.createEvent(
      action,
      dataContext,
      action.templatePresentation.clone(),
      ActionPlaces.TOOLWINDOW_TITLE,
      ActionUiKind.NONE,
      inputEvent,
    )
    ActionUtil.performAction(action, event)
  }

  companion object {
    private const val HEIGHT = 22
    private const val HORIZONTAL_PADDING = 6
    private const val TEXT_CHEVRON_GAP = 4
    private const val CHEVRON_WIDTH = 7
    private const val CHEVRON_HEIGHT = 4
  }
}
