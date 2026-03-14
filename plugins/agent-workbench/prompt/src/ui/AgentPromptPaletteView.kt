// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.markdown.utils.convertMarkdownToHtml
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.EditorTextField
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.tabbedPaneHeader
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.text.DefaultCaret

private val POPUP_PREFERRED_SIZE = JBUI.size(680, 380)
private val POPUP_MINIMUM_SIZE = JBUI.size(680, 260)
private val EXISTING_TASK_PANEL_PREFERRED_SIZE = JBUI.size(0, 140)
private val EXISTING_TASK_PANEL_MINIMUM_SIZE = JBUI.size(0, 90)
private const val EXISTING_TASK_VISIBLE_ROWS = 4
private val PROMPT_PANEL_MINIMUM_SIZE = JBUI.size(0, 72)
private const val CARD_EDITOR = "editor"
private const val CARD_PREVIEW = "preview"

internal data class AgentPromptPaletteView(
  @JvmField val rootPanel: JPanel,
  @JvmField val promptPanel: JPanel,
  @JvmField val composerContextPanel: JPanel,
  @JvmField val bottomPanel: JPanel,
  @JvmField val tabbedPane: JBTabbedPane,
  @JvmField val providerIconLabel: JBLabel,
  @JvmField val addContextButton: ActionLink,
  @JvmField val existingTaskListModel: DefaultListModel<ThreadEntry>,
  @JvmField val existingTaskList: JBList<ThreadEntry>,
  @JvmField val existingTaskScrollPane: JBScrollPane,
  @JvmField val footerLabel: JBLabel,
  @JvmField val providerOptionsPanel: JPanel?,
)

private class ComposerContextActionLink(text: @Nls String) : ActionLink(text) {
  var onVisibilityChanged: (() -> Unit)? = null

  override fun setVisible(aFlag: Boolean) {
    val visibilityChanged = isVisible != aFlag
    super.setVisible(aFlag)
    if (visibilityChanged) {
      onVisibilityChanged?.invoke()
    }
  }
}

internal fun createAgentPromptPaletteView(
  promptArea: EditorTextField,
  contextChipsPanel: JPanel,
  providerOptionsPanel: JPanel? = null,
  onProviderIconClicked: () -> Unit,
  onExistingTaskSelected: (ThreadEntry) -> Unit,
): AgentPromptPaletteView {
  val providerIconLabel = JBLabel().apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.provider.selector.tooltip")))
    border = JBUI.Borders.empty()
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        onProviderIconClicked()
      }
    })
  }

  val previewPane = JEditorPane().apply {
    editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
    isEditable = false
    isOpaque = true
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    border = JBUI.Borders.empty(4)
    (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
  }
  val previewScrollPane = JBScrollPane(previewPane).apply {
    border = null
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  val promptCardLayout = CardLayout()
  val promptCardPanel = JPanel(promptCardLayout).apply {
    isOpaque = false
    add(promptArea, CARD_EDITOR)
    add(previewScrollPane, CARD_PREVIEW)
  }
  promptCardLayout.show(promptCardPanel, CARD_EDITOR)

  val previewToggle = JBLabel(AllIcons.Actions.Preview).apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.preview.toggle.tooltip")))
    border = JBUI.Borders.empty()
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        val showing = promptCardPanel.getClientProperty("previewShowing") == true
        if (showing) {
          promptCardLayout.show(promptCardPanel, CARD_EDITOR)
          promptCardPanel.putClientProperty("previewShowing", false)
          icon = AllIcons.Actions.Preview
        }
        else {
          val markdown = promptArea.text
          val html = convertMarkdownToHtml(markdown)
          previewPane.text = "<html><body>$html</body></html>"
          previewPane.caretPosition = 0
          promptCardLayout.show(promptCardPanel, CARD_PREVIEW)
          promptCardPanel.putClientProperty("previewShowing", true)
          icon = AllIcons.Actions.Edit
        }
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
      if (providerOptionsPanel != null) {
        cell(providerOptionsPanel)
          .align(AlignX.RIGHT)
          .customize(UnscaledGaps(left = controlToIconGap))
      }
      cell(previewToggle)
        .align(AlignX.RIGHT)
        .customize(UnscaledGaps(left = if (providerOptionsPanel != null) controlToIconGap else controlsLeftGap))
      cell(providerIconLabel)
        .align(AlignX.RIGHT)
        .customize(UnscaledGaps(left = controlToIconGap))
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

  val addContextButton = ComposerContextActionLink(AgentPromptBundle.message("popup.context.add")).apply {
    autoHideOnDisable = false
    isFocusable = false
    setDropDownLinkIcon()
    border = JBUI.Borders.empty()
    DialogUtil.registerMnemonic(this)
  }

  val contextChipsContainer = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(contextChipsPanel, BorderLayout.CENTER)
  }
  val promptActionsBar = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(4)
    add(addContextButton, BorderLayout.WEST)
  }
  val composerContextPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(6)
    addToTop(contextChipsContainer)
    addToBottom(promptActionsBar)
  }

  val promptPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(8, 12)
    add(promptCardPanel, BorderLayout.CENTER)
    add(composerContextPanel, BorderLayout.SOUTH)
    minimumSize = PROMPT_PANEL_MINIMUM_SIZE
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
    addToCenter(existingTaskScrollPane)
    addToBottom(footerLabel)
  }
  installComposerContextVisibilitySync(
    contextChipsPanel = contextChipsPanel,
    contextChipsContainer = contextChipsContainer,
    addContextControl = addContextButton,
    composerContextPanel = composerContextPanel,
    layoutParent = promptPanel,
  )

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
    promptPanel = promptPanel,
    composerContextPanel = composerContextPanel,
    bottomPanel = bottomPanel,
    tabbedPane = tabbedPane,
    providerIconLabel = providerIconLabel,
    addContextButton = addContextButton,
    existingTaskListModel = existingTaskListModel,
    existingTaskList = existingTaskList,
    existingTaskScrollPane = existingTaskScrollPane,
    footerLabel = footerLabel,
    providerOptionsPanel = providerOptionsPanel,
  )
}

private fun installComposerContextVisibilitySync(
  contextChipsPanel: JPanel,
  contextChipsContainer: JPanel,
  addContextControl: ComposerContextActionLink,
  composerContextPanel: JPanel,
  layoutParent: JPanel,
) {
  fun syncVisibility() {
    val hasContextChips = contextChipsPanel.componentCount > 0
    val shouldShowComposerContext = hasContextChips || addContextControl.isVisible
    val chipsVisibilityChanged = contextChipsContainer.isVisible != hasContextChips
    val panelVisibilityChanged = composerContextPanel.isVisible != shouldShowComposerContext
    if (!chipsVisibilityChanged && !panelVisibilityChanged) {
      return
    }

    contextChipsContainer.isVisible = hasContextChips
    composerContextPanel.isVisible = shouldShowComposerContext
    layoutParent.revalidate()
    layoutParent.repaint()
  }

  contextChipsPanel.addContainerListener(object : ContainerAdapter() {
    override fun componentAdded(event: ContainerEvent?) {
      syncVisibility()
    }

    override fun componentRemoved(event: ContainerEvent?) {
      syncVisibility()
    }
  })
  addContextControl.onVisibilityChanged = ::syncVisibility

  syncVisibility()
}
