// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import com.intellij.platform.problemView.security.icons.SecurityProblemsViewIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.render.RenderingUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class ProblemsViewSubTabItemRenderer : ListCellRenderer<ProblemsViewSubTabItem> {

  private val titleLabel: JBLabel = JBLabel()
  private val countLabel: JBLabel = JBLabel().apply {
    font = JBFont.h3().asBold()
    iconTextGap = JBUI.scale(COUNT_ICON_TEXT_GAP)
  }

  private val detailLabel: JBLabel = fixedHeightLabel().apply { font = JBFont.medium() }
  private val component: SelectablePanel = createComponent()

  override fun getListCellRendererComponent(
    list: JList<out ProblemsViewSubTabItem>,
    value: ProblemsViewSubTabItem,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val presentation = value.presentation
    // the row shows the title of a sub-tab, so it keeps the title capitalization
    @Suppress("DialogTitleCapitalization")
    titleLabel.text = presentation.title
    val count = presentation.problemCount ?: 0

    val foundNoIssues = count == 0 && presentation.foundNoIssues
    countLabel.text = when {
      count > 0 -> SecurityProblemsViewBundle.message("security.problems.view.sub.tab.issue.count", count)
      foundNoIssues -> SecurityProblemsViewBundle.message("security.problems.view.sub.tab.no.issues")
      else -> ""
    }
    countLabel.icon = when {
      count > 0 -> presentation.icon ?: SecurityProblemsViewIcons.Status.IssuesFound
      foundNoIssues -> presentation.icon ?: SecurityProblemsViewIcons.Status.NoIssuesFound
      else -> null
    }
    countLabel.isVisible = count > 0 || foundNoIssues
    detailLabel.text = presentation.detail ?: ""

    val selectionColor = when {
      isSelected -> RenderingUtil.getSelectionBackground(list)
      index == ListHoverListener.getHoveredIndex(list) -> RenderingUtil.getHoverBackground(list) ?: RenderingUtil.getBackground(list)
      else -> null
    }
    component.background = RenderingUtil.getBackground(list)
    component.selectionColor = selectionColor
    val foreground = RenderingUtil.getForeground(list, isSelected)
    val secondaryForeground = NamedColorUtil.getInactiveTextColor()
    titleLabel.foreground = foreground
    countLabel.foreground = foreground
    detailLabel.foreground = secondaryForeground

    // a screen reader reads the row, so its name carries the count
    val accessible = component.accessibleContext
    accessible.accessibleName = when {
      count > 0 -> SecurityProblemsViewBundle.message("security.problems.view.sub.tab.accessible.name", presentation.title, count)
      foundNoIssues -> SecurityProblemsViewBundle.message("security.problems.view.sub.tab.accessible.name.no.issues", presentation.title)
      else -> presentation.title
    }
    accessible.accessibleDescription = presentation.detail
    return component
  }

  private fun createComponent(): SelectablePanel = SelectablePanel().apply {
    layout = BorderLayout()
    selectionArc = JBUI.scale(6)
    selectionInsets = JBUI.insets(OUTER_VERTICAL_GAP, OUTER_HORIZONTAL_GAP)
    border = JBUI.Borders.empty(OUTER_VERTICAL_GAP, OUTER_HORIZONTAL_GAP)
    add(createContent(), BorderLayout.CENTER)
  }

  private fun createContent(): JPanel = JPanel(BorderLayout(0, JBUI.scale(INNER_LINE_GAP))).apply {
    isOpaque = false
    border = JBUI.Borders.empty(INNER_PADDING)
    add(titleLabel, BorderLayout.NORTH)
    add(createDetails(), BorderLayout.CENTER)
  }

  private fun createDetails(): JPanel = JPanel(BorderLayout(0, JBUI.scale(INNER_LINE_GAP))).apply {
    isOpaque = false
    add(countLabel, BorderLayout.NORTH)
    add(detailLabel, BorderLayout.CENTER)
  }

  private fun fixedHeightLabel(): JBLabel = object : JBLabel() {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      return Dimension(size.width, maxOf(size.height, getFontMetrics(font).height))
    }
  }

  private companion object {
    const val OUTER_VERTICAL_GAP: Int = 0
    const val OUTER_HORIZONTAL_GAP: Int = 8
    const val INNER_PADDING: Int = 8
    const val INNER_LINE_GAP: Int = 2
    const val COUNT_ICON_TEXT_GAP: Int = 4
  }
}
