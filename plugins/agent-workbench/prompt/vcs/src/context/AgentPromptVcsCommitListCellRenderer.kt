// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.context

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class AgentPromptVcsCommitListCellRenderer(
  private val showRootNames: Boolean,
) : JPanel(BorderLayout(JBUI.scale(6), 0)), ListCellRenderer<CommitPickerEntry> {
  private val nodeComponent = CommitNodeComponent()
  private val subjectComponent = SimpleColoredComponent().apply {
    isOpaque = false
  }
  private val detailsComponent = SimpleColoredComponent().apply {
    isOpaque = false
  }

  init {
    isOpaque = true
    border = JBUI.Borders.empty(3, 6)
    add(nodeComponent, BorderLayout.WEST)
    add(
      JPanel(BorderLayout()).apply {
        isOpaque = false
        add(subjectComponent, BorderLayout.NORTH)
        add(detailsComponent, BorderLayout.SOUTH)
      },
      BorderLayout.CENTER,
    )
  }

  override fun getListCellRendererComponent(
    list: JList<out CommitPickerEntry>,
    value: CommitPickerEntry?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    background = UIUtil.getListBackground(isSelected, cellHasFocus)
    val foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
    val secondaryForeground = if (isSelected) foreground else UIUtil.getContextHelpForeground()
    val detailsAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, secondaryForeground)

    nodeComponent.foreground = secondaryForeground
    nodeComponent.type = CommitNodeType.forListItem(index, list.model.size)

    subjectComponent.clear()
    detailsComponent.clear()
    if (value == null) {
      return this
    }

    subjectComponent.append(value.shortHash(), SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, foreground))
    subjectComponent.append("  ${value.subject}", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground))

    val details: @NlsSafe String = value.detailsLine(showRootNames)
    detailsComponent.isVisible = details.isNotEmpty()
    if (details.isNotEmpty()) {
      detailsComponent.append(details, detailsAttributes)
    }
    return this
  }
}

private fun CommitPickerEntry.detailsLine(showRootNames: Boolean): @NlsSafe String {
  val details = ArrayList<String>()
  author?.takeIf { it.isNotBlank() }?.let(details::add)
  commitTimeMs?.let { timestamp -> details += DateFormatUtil.formatPrettyDateTime(timestamp) }
  rootName?.takeIf { showRootNames && it.isNotBlank() }?.let(details::add)
  return details.joinToString(separator = "  ")
}

private fun CommitPickerEntry.shortHash(): @NlsSafe String {
  return hash.take(8)
}

private enum class CommitNodeType {
  SINGLE,
  FIRST,
  MIDDLE,
  LAST;

  companion object {
    fun forListItem(itemIndex: Int, listSize: Int): CommitNodeType = when {
      listSize <= 1 -> SINGLE
      itemIndex <= 0 -> FIRST
      itemIndex == listSize - 1 -> LAST
      else -> MIDDLE
    }
  }
}

private class CommitNodeComponent : JComponent() {
  var type: CommitNodeType = CommitNodeType.SINGLE

  init {
    isOpaque = false
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(JBUI.scale(14), JBUI.scale(32))
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val centerX = width / 2.0
    val centerY = height / 2.0
    val lineWidth = JBUI.scale(2).toDouble()
    val radius = JBUI.scale(3).toDouble()

    g2.color = foreground
    if (type == CommitNodeType.LAST || type == CommitNodeType.MIDDLE) {
      g2.fillRect((centerX - lineWidth / 2).toInt(), 0, lineWidth.toInt().coerceAtLeast(1), centerY.toInt())
    }
    if (type == CommitNodeType.FIRST || type == CommitNodeType.MIDDLE) {
      g2.fillRect((centerX - lineWidth / 2).toInt(), centerY.toInt(), lineWidth.toInt().coerceAtLeast(1), height - centerY.toInt())
    }
    g2.fill(Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0))
  }
}
