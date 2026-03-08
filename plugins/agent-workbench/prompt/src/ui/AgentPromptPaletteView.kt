// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.tabbedPaneHeader
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

private val POPUP_PREFERRED_SIZE = JBUI.size(680, 380)
private val POPUP_MINIMUM_SIZE = JBUI.size(680, 260)
private val EXISTING_TASK_PANEL_PREFERRED_SIZE = JBUI.size(0, 140)
private val EXISTING_TASK_PANEL_MINIMUM_SIZE = JBUI.size(0, 90)
private const val EXISTING_TASK_VISIBLE_ROWS = 4
private val PROMPT_PANEL_MINIMUM_SIZE = JBUI.size(0, 72)

internal data class AgentPromptPaletteView(
  val rootPanel: JPanel,
  val tabbedPane: JBTabbedPane,
  val providerIconLabel: JBLabel,
  val existingTaskListModel: DefaultListModel<ThreadEntry>,
  val existingTaskList: JBList<ThreadEntry>,
  val existingTaskScrollPane: JBScrollPane,
  val footerLabel: JBLabel,
  val planModeCheckBox: JBCheckBox?,
)

internal fun createAgentPromptPaletteView(
  promptArea: JBTextArea,
  contextChipsPanel: JPanel,
  planModeCheckBox: JBCheckBox? = null,
  onProviderIconClicked: () -> Unit,
  onExistingTaskSelected: (ThreadEntry) -> Unit,
): AgentPromptPaletteView {
  val providerIconLabel = JBLabel().apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    toolTipText = AgentPromptBundle.message("popup.provider.selector.tooltip")
    border = JBUI.Borders.empty()
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        onProviderIconClicked()
      }
    })
  }

  lateinit var tabbedPane: JBTabbedPane
  val headerControlsInsets = JBUI.CurrentTheme.BigPopup.headerToolbarInsets()
  val controlsLeftGap = headerControlsInsets.left
  val controlToIconGap = headerControlsInsets.right
  val spacer = JPanel().apply {
    isOpaque = false
  }
  val headerPanel = panel {
    row {
      tabbedPane = tabbedPaneHeader()
        .customize(UnscaledGaps.EMPTY)
        .applyToComponent {
          font = JBFont.regular()
          background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
          isFocusable = false
        }
        .component
      cell(tabbedPane)
      cell(spacer)
        .resizableColumn()
      if (planModeCheckBox != null) {
        cell(planModeCheckBox)
          .align(AlignX.RIGHT)
          .customize(UnscaledGaps(left = controlsLeftGap))
      }
      cell(providerIconLabel)
        .align(AlignX.RIGHT)
        .customize(UnscaledGaps(left = if (planModeCheckBox != null) controlToIconGap else controlsLeftGap))
    }
  }
  headerPanel.apply {
    border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      JBUI.CurrentTheme.BigPopup.headerBorder(),
    )
    background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
  }

  tabbedPane.addTab(AgentPromptBundle.message("popup.target.new"), JPanel().apply {
    putClientProperty("targetMode", PromptTargetMode.NEW_TASK)
  })
  tabbedPane.addTab(AgentPromptBundle.message("popup.target.existing"), JPanel().apply {
    putClientProperty("targetMode", PromptTargetMode.EXISTING_TASK)
  })

  val existingTaskListModel = DefaultListModel<ThreadEntry>()
  val existingTaskList = JBList(existingTaskListModel).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = ExistingTaskCellRenderer()
    visibleRowCount = EXISTING_TASK_VISIBLE_ROWS
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    emptyText.text = AgentPromptBundle.message("popup.existing.loading")
    addListSelectionListener {
      if (!it.valueIsAdjusting) {
        selectedValue?.let(onExistingTaskSelected)
      }
    }
  }
  val existingTaskScrollPane = JBScrollPane(existingTaskList).apply {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1, 0, 0, 0)
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    preferredSize = EXISTING_TASK_PANEL_PREFERRED_SIZE
    minimumSize = EXISTING_TASK_PANEL_MINIMUM_SIZE
  }

  promptArea.emptyText.text = AgentPromptBundle.message("popup.prompt.placeholder")
  promptArea.background = JBUI.CurrentTheme.Popup.BACKGROUND
  val promptScrollPane = JBScrollPane(promptArea).apply {
    border = null
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    minimumSize = PROMPT_PANEL_MINIMUM_SIZE
  }
  val promptPanel = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty(8, 12)
    add(
      promptScrollPane,
      BorderLayout.CENTER,
    )
    minimumSize = PROMPT_PANEL_MINIMUM_SIZE
  }

  val contextRow = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(2, 12, 0, 12)
    add(contextChipsPanel, BorderLayout.CENTER)
  }

  val footerLabel = JBLabel("").apply {
    text = AgentPromptBundle.message("popup.footer.hint")
    foreground = JBUI.CurrentTheme.Advertiser.foreground()
    background = JBUI.CurrentTheme.Advertiser.background()
    border = JBUI.CurrentTheme.Advertiser.border()
    isOpaque = true
  }

  val bottomPanel = BorderLayoutPanel().apply {
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1, 0, 0, 0)
    addToTop(contextRow)
    addToCenter(existingTaskScrollPane)
    addToBottom(footerLabel)
  }

  val rootPanel = BorderLayoutPanel().apply {
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    preferredSize = POPUP_PREFERRED_SIZE
    minimumSize = POPUP_MINIMUM_SIZE
    addToTop(headerPanel)
    addToCenter(promptPanel)
    addToBottom(bottomPanel)
  }

  WindowMoveListener(rootPanel).installTo(headerPanel)

  return AgentPromptPaletteView(
    rootPanel = rootPanel,
    tabbedPane = tabbedPane,
    providerIconLabel = providerIconLabel,
    existingTaskListModel = existingTaskListModel,
    existingTaskList = existingTaskList,
    existingTaskScrollPane = existingTaskScrollPane,
    footerLabel = footerLabel,
    planModeCheckBox = planModeCheckBox,
  )
}
