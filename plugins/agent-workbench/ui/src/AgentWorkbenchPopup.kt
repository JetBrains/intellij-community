// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ui

import com.intellij.ide.setToolTipText
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.popup.WizardPopup
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

data class AgentWorkbenchPopupRow(
  val text: @Nls String,
  val separatorText: @Nls String? = null,
  val primaryIcon: Icon? = null,
  val secondaryIcon: Icon? = null,
  val tooltipText: @Nls String? = null,
  val selected: Boolean = false,
  val selectable: Boolean = true,
  val subRows: List<AgentWorkbenchPopupRow> = emptyList(),
  val onChosen: (() -> Unit)? = null,
)

class AgentWorkbenchPopupStep(
  rows: List<AgentWorkbenchPopupRow>,
) : BaseListPopupStep<AgentWorkbenchPopupRow>(null, rows), ListPopupStepEx<AgentWorkbenchPopupRow> {
  init {
    defaultOptionIndex = rows.indexOfFirst { row -> row.selected }
  }

  override fun getTextFor(value: AgentWorkbenchPopupRow): String {
    return value.text
  }

  override fun getIconFor(value: AgentWorkbenchPopupRow): Icon? {
    return null
  }

  override fun getSelectedIconFor(value: AgentWorkbenchPopupRow): Icon? {
    return null
  }

  override fun getSecondaryIconFor(t: AgentWorkbenchPopupRow): Icon? {
    return null
  }

  override fun getTooltipTextFor(value: AgentWorkbenchPopupRow): String? {
    return value.tooltipText
  }

  override fun getSeparatorAbove(value: AgentWorkbenchPopupRow): ListSeparator? {
    return value.separatorText?.let(::ListSeparator)
  }

  override fun isSelectable(value: AgentWorkbenchPopupRow): Boolean {
    return value.selectable
  }

  override fun hasSubstep(selectedValue: AgentWorkbenchPopupRow?): Boolean {
    return selectedValue?.subRows?.isNotEmpty() == true
  }

  override fun onChosen(selectedValue: AgentWorkbenchPopupRow, finalChoice: Boolean): PopupStep<*>? {
    if (selectedValue.subRows.isNotEmpty()) {
      return AgentWorkbenchPopupStep(selectedValue.subRows)
    }
    if (selectedValue.selectable) {
      selectedValue.onChosen?.invoke()
    }
    return FINAL_CHOICE
  }

  override fun isSpeedSearchEnabled(): Boolean {
    return true
  }

  override fun getIndexedString(value: AgentWorkbenchPopupRow): String {
    return value.text
  }

  override fun setEmptyText(emptyText: StatusText) {
  }
}

class AgentWorkbenchListPopup(
  project: Project?,
  step: AgentWorkbenchPopupStep,
  parent: WizardPopup? = null,
  parentValue: Any? = null,
) : ListPopupImpl(project, parent, step, parentValue) {
  override fun getListElementRenderer(): ListCellRenderer<*> {
    return AgentWorkbenchPopupRowRenderer()
  }

  override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?): ListPopupImpl {
    return AgentWorkbenchListPopup(parent?.project, step as AgentWorkbenchPopupStep, parent, parentValue)
  }
}

fun createAgentWorkbenchListPopup(project: Project?, rows: List<AgentWorkbenchPopupRow>): ListPopup {
  return AgentWorkbenchListPopup(project, AgentWorkbenchPopupStep(rows))
}

class AgentWorkbenchPopupRowRenderer : ListCellRenderer<AgentWorkbenchPopupRow> {
  private val rowComponent = AgentWorkbenchPopupRowComponent()
  private val rowWithSeparatorComponent = AgentWorkbenchPopupRowWithSeparatorComponent()

  override fun getListCellRendererComponent(
    list: JList<out AgentWorkbenchPopupRow>,
    value: AgentWorkbenchPopupRow,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val model = list.model as? ListPopupModel<*>
    return if (model?.isSeparatorAboveOf(value) == true) {
      rowWithSeparatorComponent.updateSeparator(model.getCaptionAboveOf(value), index == 0)
      rowWithSeparatorComponent.update(value, isSelected)
      rowWithSeparatorComponent.component
    }
    else {
      rowComponent.update(value, isSelected)
      rowComponent.component
    }
  }
}

private open class AgentWorkbenchPopupRowComponent {
  protected val selectablePanel: SelectablePanel
  private val iconLabel = JLabel()
  private val textLabel = JLabel()
  private val secondaryIconLabel = JLabel()

  open val component: JComponent
    get() = selectablePanel

  fun update(row: AgentWorkbenchPopupRow, isSelected: Boolean) {
    iconLabel.icon = row.primaryIcon
    iconLabel.isVisible = row.primaryIcon != null
    textLabel.text = row.text
    textLabel.isEnabled = row.selectable
    textLabel.foreground = when {
      isSelected && row.selectable -> NamedColorUtil.getListSelectionForeground(true)
      else -> UIUtil.getListForeground()
    }
    secondaryIconLabel.icon = row.secondaryIcon
    secondaryIconLabel.isVisible = row.secondaryIcon != null
    val tooltipText = row.tooltipText?.let(HtmlChunk::text)
    secondaryIconLabel.setToolTipText(tooltipText)
    selectablePanel.setToolTipText(tooltipText)
    selectablePanel.selectionColor = if (isSelected && row.selectable) UIUtil.getListSelectionBackground(true) else null
    selectablePanel.accessibleContext.accessibleName = AccessibleContextUtil.combineAccessibleStrings(row.text, ", ", row.tooltipText)
  }

  init {
    iconLabel.isOpaque = false
    textLabel.isOpaque = false
    secondaryIconLabel.isOpaque = false
    iconLabel.border = JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap())
    secondaryIconLabel.border = JBUI.Borders.emptyLeft(JBUI.CurrentTheme.ActionsList.elementIconGap() + 1)

    val content = JPanel(BorderLayout()).apply {
      isOpaque = false
      add(iconLabel, BorderLayout.WEST)
      add(textLabel, BorderLayout.CENTER)
      add(secondaryIconLabel, BorderLayout.EAST)
    }
    selectablePanel = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND).apply {
      isOpaque = true
    }
    PopupUtil.configListRendererFixedHeight(selectablePanel)
  }
}

private class AgentWorkbenchPopupRowWithSeparatorComponent : AgentWorkbenchPopupRowComponent() {
  private val separatorLabel = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
  private val separatorPanel = JPanel(BorderLayout()).apply {
    isOpaque = true
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    add(separatorLabel)
  }

  override val component: JComponent = NonOpaquePanel(BorderLayout()).apply {
    add(separatorPanel, BorderLayout.NORTH)
    add(selectablePanel, BorderLayout.CENTER)
  }

  fun updateSeparator(text: @Nls String?, hideLine: Boolean) {
    separatorLabel.caption = text
    @Suppress("UsePropertyAccessSyntax")
    separatorLabel.setHideLine(hideLine)
    separatorLabel.revalidate()
  }
}
