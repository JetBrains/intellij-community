// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-tree.spec.md

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
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

internal enum class AgentSessionsActivityCounterTone { ATTENTION, DEFAULT }

internal class AgentSessionsActivityCounterComponent(
  private val action: AnAction,
  private val accentColor: Color,
  private val tone: AgentSessionsActivityCounterTone,
  private val actionPlace: String = ActionPlaces.TOOLWINDOW_TITLE,
) : JComponent() {
  private var countText: String = "0"

  init {
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    font = JBFont.label()
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1 && isEnabled) performAction(e)
      }
    })
  }

  fun update(presentation: Presentation) {
    val newText = presentation.text.orEmpty().ifEmpty { "0" }
    val changed = newText != countText
    countText = newText
    isEnabled = presentation.isEnabled
    cursor = if (isEnabled) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
    @Suppress("UseHtmlChunkToolTip") // plain Nls description from a properties bundle
    run { toolTipText = presentation.description }
    getAccessibleContext()?.accessibleName = presentation.description ?: presentation.text
    if (changed) revalidate()
    repaint()
  }

  override fun getPreferredSize(): Dimension {
    val metrics = getFontMetrics(countFont())
    val textWidth = metrics.stringWidth(countText).coerceAtLeast(metrics.stringWidth(MIN_COUNT_WIDTH_TEXT))
    val padding = JBUI.scale(HORIZONTAL_PADDING)
    val width = padding * 2 + JBUI.scale(MARKER_SIZE) + JBUI.scale(MARKER_TEXT_GAP) + textWidth
    return Dimension(width.coerceAtLeast(JBUI.scale(MIN_WIDTH)), JBUI.scale(HEIGHT))
  }

  override fun getMinimumSize(): Dimension = preferredSize

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      val h = height
      val isZero = countText == "0"
      val padding = JBUI.scale(HORIZONTAL_PADDING)
      val markerSize = JBUI.scale(MARKER_SIZE)
      val markerY = (h - markerSize) / 2

      g2.color = markerColor(isZero)
      g2.fillOval(padding, markerY, markerSize, markerSize)

      g2.font = countFont()
      val metrics = g2.fontMetrics
      val textX = padding + JBUI.scale(MARKER_SIZE) + JBUI.scale(MARKER_TEXT_GAP)
      val textY = (h - metrics.height) / 2 + metrics.ascent
      g2.color = textColor(isZero)
      g2.drawString(countText, textX, textY)
    }
    finally {
      g2.dispose()
    }
  }

  private fun markerColor(isZero: Boolean): Color = when {
    isZero -> UIUtil.getLabelDisabledForeground()
    else -> accentColor
  }

  private fun textColor(isZero: Boolean): Color = when {
    isZero -> UIUtil.getLabelDisabledForeground()
    else -> UIUtil.getLabelForeground()
  }

  private fun countFont() = if (countText != "0" && tone == AgentSessionsActivityCounterTone.ATTENTION) JBFont.label().asBold() else font

  private fun performAction(inputEvent: MouseEvent) {
    val dataContext = ActionToolbar.getDataContextFor(this)
    val event = AnActionEvent.createEvent(
      action,
      dataContext,
      action.templatePresentation.clone(),
      actionPlace,
      ActionUiKind.NONE,
      inputEvent,
    )
    ActionUtil.performAction(action, event)
  }

  companion object {
    private const val HEIGHT = 22
    private const val MIN_WIDTH = 26
    private const val HORIZONTAL_PADDING = 5
    private const val MARKER_SIZE = 6
    private const val MARKER_TEXT_GAP = 4
    private const val MIN_COUNT_WIDTH_TEXT = "0"
  }
}
