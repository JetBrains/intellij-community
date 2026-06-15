// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.setToolTipText
import com.intellij.markdown.utils.convertMarkdownToHtml
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
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
import com.intellij.util.ui.Advertiser
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.text.DefaultCaret

internal val AGENT_PROMPT_PALETTE_PREFERRED_SIZE: Dimension
  get() = JBUI.size(680, 380)
internal val AGENT_PROMPT_PALETTE_MINIMUM_SIZE: Dimension
  get() = JBUI.size(520, 260)
private val EXISTING_TASK_PANEL_PREFERRED_SIZE = JBUI.size(0, 90)
private val EXISTING_TASK_PANEL_MINIMUM_SIZE = JBUI.size(0, 60)
private const val EXISTING_TASK_VISIBLE_ROWS = 3
private val PROMPT_PANEL_MINIMUM_SIZE = JBUI.size(0, 120)

@NonNls
private const val HEADER_ACTIONS_PLACE = "AgentPromptPalette.Header"
private const val CARD_EDITOR = "editor"
private const val CARD_PREVIEW = "preview"

private fun headerIconButtonSize(): Dimension = Dimension(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

internal data class AgentPromptPaletteView(
  @JvmField val rootPanel: JPanel,
  @JvmField val promptPanel: JPanel,
  @JvmField val promptEditorPanel: JPanel,
  @JvmField val suggestionsPanel: JPanel,
  @JvmField val composerContextPanel: JPanel,
  @JvmField val bottomPanel: JPanel,
  @JvmField val tabbedPane: JBTabbedPane,
  @JvmField val profileAction: AgentPromptToolbarProfileAction,
  @JvmField val promptLibraryIconLabel: JLabel,
  @JvmField val generationSettingsPanel: JPanel,
  @JvmField val launchProfileLink: ActionLink,
  @JvmField val modelSelectorLink: ActionLink,
  @JvmField val reasoningEffortLink: ActionLink,
  @JvmField val planReasoningEffortLink: ActionLink,
  @JvmField val defaultProfileActionControl: AgentPromptDefaultProfileActionControl,
  @JvmField val addContextButton: ActionLink,
  @JvmField val existingTaskListModel: DefaultListModel<ThreadEntry>,
  @JvmField val existingTaskList: JBList<ThreadEntry>,
  @JvmField val existingTaskScrollPane: JBScrollPane,
  @JvmField val statusStrip: AgentPromptStatusStrip,
  @JvmField val rightHeaderPanel: JPanel,
  @JvmField val headerToolbar: ActionToolbar,
  @JvmField val headerControls: AgentPromptHeaderControls,
  @JvmField val containerModeAction: AgentPromptHeaderCheckBoxAction,
)

internal class AgentPromptHeaderCheckBoxAction(
  text: @Nls String,
  var selected: Boolean = false,
  private val onSelectionChanged: ((Boolean) -> Unit)? = null,
) : CheckboxAction(text), DumbAware {
  var visible: Boolean = true
  var enabled: Boolean = true
  var tooltipText: @Nls String? = null

  override fun isSelected(e: AnActionEvent): Boolean {
    return selected
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    selected = state
    onSelectionChanged?.invoke(state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = visible
    e.presentation.isEnabled = enabled
    e.presentation.description = tooltipText
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class AgentPromptStatusStrip(initialText: @Nls String) {
  private val advertiser = Advertiser().apply {
    setBorder(JBUI.CurrentTheme.BigPopup.advertiserBorder())
    setBackground(JBUI.CurrentTheme.BigPopup.advertiserBackground())
    setForeground(JBUI.CurrentTheme.BigPopup.advertiserForeground())
  }

  @JvmField
  val component: JComponent = advertiser.adComponent

  var text: @Nls String = ""
    private set

  init {
    showInfo(initialText)
  }

  fun showInfo(message: @Nls String) {
    show(message, JBUI.CurrentTheme.BigPopup.advertiserForeground())
  }

  fun showError(message: @Nls String) {
    show(message, NamedColorUtil.getErrorForeground())
  }

  private fun show(message: @Nls String, foreground: Color) {
    val previousText = text
    text = message
    advertiser.clearAdvertisements()
    advertiser.setForeground(foreground)
    advertiser.addAdvertisement(message, null)
    component.accessibleContext?.let { accessibleContext ->
      accessibleContext.accessibleName = message
      accessibleContext.firePropertyChange(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, previousText, message)
    }
  }
}

internal class AgentPromptHeaderControls(
  private val rootGroup: DefaultActionGroup,
  @JvmField val toolbar: ActionToolbar,
  @JvmField val toolbarComponent: JComponent,
  @JvmField val containerModeAction: AgentPromptHeaderCheckBoxAction,
  private val previewAction: AgentPromptToolbarIconAction,
  private val promptLibraryAction: AgentPromptToolbarIconAction,
  private val profileAction: AgentPromptToolbarProfileAction,
  private val pinAction: AgentPromptToolbarIconToggleAction,
) {
  private var providerOptionsVisible = true

  var providerOptionActions: List<AgentPromptHeaderCheckBoxAction> = emptyList()
    private set

  fun setProviderOptionActions(actions: List<AgentPromptHeaderCheckBoxAction>) {
    providerOptionActions = actions
    providerOptionActions.forEach { action -> action.visible = providerOptionsVisible }
    rebuildActions()
  }

  fun setProviderOptionsVisible(visible: Boolean) {
    providerOptionsVisible = visible
    providerOptionActions.forEach { action -> action.visible = visible }
    updateActions()
  }

  fun setContainerModeVisible(visible: Boolean) {
    containerModeAction.visible = visible
    updateActions()
  }

  fun setContainerModeState(visible: Boolean, enabled: Boolean, selected: Boolean, tooltipText: @Nls String?) {
    containerModeAction.visible = visible
    containerModeAction.enabled = enabled
    containerModeAction.selected = selected
    containerModeAction.tooltipText = tooltipText
    updateActions()
  }

  fun updateActions() {
    @Suppress("DEPRECATION")
    toolbar.updateActionsImmediately()
  }

  private fun rebuildActions() {
    rootGroup.removeAll()
    rootGroup.add(containerModeAction)
    providerOptionActions.forEach(rootGroup::add)
    rootGroup.add(previewAction)
    rootGroup.add(promptLibraryAction)
    rootGroup.add(profileAction)
    rootGroup.add(pinAction)
    updateActions()
  }
}

internal class AgentPromptToolbarIconToggleAction(
  text: @Nls String,
  initialIcon: Icon,
  private val isSet: () -> Boolean,
  private val onToggleClick: () -> Unit,
) : DumbAwareToggleAction(text, null, initialIcon) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = isSet()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    onToggleClick.invoke()
  }
}

internal class HeaderActionLink(text: @Nls String) : ActionLink(text) {
  var onVisibilityChanged: (() -> Unit)? = null

  override fun setVisible(aFlag: Boolean) {
    val visibilityChanged = isVisible != aFlag
    super.setVisible(aFlag)
    if (visibilityChanged) {
      onVisibilityChanged?.invoke()
    }
  }
}

internal class AgentPromptToolbarProfileAction(
  initialText: @Nls String,
  initialDescription: @Nls String,
  initialIcon: Icon,
) : DumbAwareAction(initialText, initialDescription, initialIcon), CustomComponentAction {
  private val actionGroupProvider: () -> DefaultActionGroup
    get() = currentActionGroupProvider

  private var visible: Boolean = true
  private var enabled: Boolean = true
  private var profileText: @Nls String = initialText
  private var profileDescription: @Nls String = initialDescription
  private var profileIcon: Icon = initialIcon
  private var currentActionGroupProvider: () -> DefaultActionGroup = { DefaultActionGroup() }

  var onPresentationChanged: (() -> Unit)? = null

  @JvmField
  val link: HeaderActionLink = HeaderActionLink(initialText).apply {
    autoHideOnDisable = false
    withFont(JBUI.Fonts.smallFont())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty()
    setIcon(initialIcon, false)
    setToolTipText(HtmlChunk.text(initialDescription))
    accessibleContext.accessibleName = initialText
    accessibleContext.accessibleDescription = initialDescription
    addActionListener {
      showProfilePopup(DataManager.getInstance().getDataContext(this), this)
    }
  }

  val customComponent: JComponent
    get() = link

  val textForTest: @Nls String
    get() = profileText

  init {
    templatePresentation.text = initialText
    templatePresentation.description = initialDescription
    templatePresentation.icon = initialIcon
  }

  fun setActionGroupProvider(provider: () -> DefaultActionGroup) {
    currentActionGroupProvider = provider
  }

  fun setPresentation(
    text: @Nls String,
    description: @Nls String,
    icon: Icon,
    visible: Boolean,
    enabled: Boolean,
  ) {
    this.profileText = text
    this.profileDescription = description
    this.profileIcon = icon
    this.visible = visible
    this.enabled = enabled
    templatePresentation.text = text
    templatePresentation.description = description
    templatePresentation.icon = icon
    link.text = text
    link.isVisible = visible
    link.isEnabled = enabled
    link.setIcon(icon, false)
    link.setToolTipText(HtmlChunk.text(description))
    link.accessibleContext.accessibleName = text
    link.accessibleContext.accessibleDescription = description
    link.revalidate()
    link.repaint()
    onPresentationChanged?.invoke()
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return link
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = profileText
    e.presentation.description = profileDescription
    e.presentation.icon = profileIcon
    e.presentation.isVisible = visible
    e.presentation.isEnabled = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    showProfilePopup(e.dataContext, link)
  }

  private fun showProfilePopup(dataContext: DataContext, anchor: JComponent) {
    val popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        actionGroupProvider(),
        dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
    popup.showUnderneathOf(anchor)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class AgentPromptToolbarIconAction(
  text: @Nls String,
  initialIcon: Icon,
  private val onClick: () -> Unit,
) : DumbAwareAction(text, text, initialIcon), CustomComponentAction {
  val label: JBLabel = JBLabel(initialIcon).apply {
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    setToolTipText(HtmlChunk.text(text))
    accessibleContext.accessibleName = text
    horizontalAlignment = SwingConstants.CENTER
    verticalAlignment = SwingConstants.CENTER
    minimumSize = headerIconButtonSize()
    preferredSize = headerIconButtonSize()
    maximumSize = headerIconButtonSize()
    border = JBUI.Borders.empty()
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        onClick()
      }
    })
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return label
  }

  override fun actionPerformed(e: AnActionEvent) {
    onClick()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  fun setIcon(icon: Icon) {
    templatePresentation.icon = icon
    label.icon = icon
  }
}

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
  suggestionsPanel: JPanel = JPanel(),
  contextChipsPanel: JPanel,
  pinned: () -> Boolean = { false },
  onPromptLibraryClicked: () -> Unit = {},
  onExistingTaskSelected: (ThreadEntry) -> Unit,
  onPinClicked: () -> Unit = {},
): AgentPromptPaletteView {
  val pinAction = AgentPromptToolbarIconToggleAction(
    text = AgentPromptBundle.message("popup.pin.toggle.tooltip"),
    initialIcon = AllIcons.Actions.PinTab,
    isSet = pinned,
    onToggleClick = onPinClicked,
  )

  val profileSelectorAction = AgentPromptToolbarProfileAction(
    initialText = AgentPromptBundle.message("popup.profile.header.standard"),
    initialDescription = AgentPromptBundle.message("popup.profile.tooltip"),
    initialIcon = AllIcons.Toolwindows.ToolWindowMessages,
  )
  val launchProfileLink = profileSelectorAction.link

  val promptLibraryAction = AgentPromptToolbarIconAction(
    text = AgentPromptBundle.message("popup.prompt.library.tooltip"),
    initialIcon = AllIcons.Actions.ListFiles,
    onClick = onPromptLibraryClicked,
  )
  val promptLibraryIconLabel = promptLibraryAction.label

  val previewPane = JEditorPane().apply {
    editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
    isEditable = false
    isOpaque = true
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    border = JBUI.Borders.empty(4, 6, 0, 6)
    (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
  }
  val previewScrollPane = JBScrollPane(previewPane).apply {
    border = null
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  val promptCardLayout = CardLayout()
  promptArea.border = JBUI.Borders.empty()
  val promptCardPanel = JPanel(promptCardLayout).apply {
    isOpaque = false
    add(promptArea, CARD_EDITOR)
    add(previewScrollPane, CARD_PREVIEW)
  }
  promptCardLayout.show(promptCardPanel, CARD_EDITOR)

  lateinit var previewToggleAction: AgentPromptToolbarIconAction
  previewToggleAction = AgentPromptToolbarIconAction(
    text = AgentPromptBundle.message("popup.preview.toggle.tooltip"),
    initialIcon = AllIcons.Actions.Preview,
  ) {
    val showing = promptCardPanel.getClientProperty("previewShowing") == true
    if (showing) {
      promptCardLayout.show(promptCardPanel, CARD_EDITOR)
      promptCardPanel.putClientProperty("previewShowing", false)
      previewToggleAction.setIcon(AllIcons.Actions.Preview)
    }
    else {
      val markdown = promptArea.text
      val html = convertMarkdownToHtml(markdown)
      previewPane.text = "<html><body>$html</body></html>"
      previewPane.caretPosition = 0
      promptCardLayout.show(promptCardPanel, CARD_PREVIEW)
      promptCardPanel.putClientProperty("previewShowing", true)
      previewToggleAction.setIcon(AllIcons.Actions.Edit)
    }
  }

  val containerModeAction = AgentPromptHeaderCheckBoxAction(AgentPromptBundle.message("popup.option.container.mode")).apply {
    visible = false
  }
  val headerActionsGroup = DefaultActionGroup()
  val headerToolbar = ActionManager.getInstance().createActionToolbar(HEADER_ACTIONS_PLACE, headerActionsGroup, true).apply {
    layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    setReservePlaceAutoPopupIcon(true)
    (this as? ActionToolbarImpl)?.setSkipWindowAdjustments(true)
    component.isOpaque = false
    component.border = JBUI.Borders.empty(JBUI.CurrentTheme.BigPopup.headerToolbarInsets())
  }
  val headerControls = AgentPromptHeaderControls(
    rootGroup = headerActionsGroup,
    toolbar = headerToolbar,
    toolbarComponent = headerToolbar.component,
    containerModeAction = containerModeAction,
    previewAction = previewToggleAction,
    promptLibraryAction = promptLibraryAction,
    profileAction = profileSelectorAction,
    pinAction = pinAction,
  )
  launchProfileLink.onVisibilityChanged = headerControls::updateActions
  profileSelectorAction.onPresentationChanged = headerControls::updateActions

  lateinit var tabbedPane: JBTabbedPane
  val rightHeaderPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(headerToolbar.component, BorderLayout.CENTER)
  }
  headerControls.setProviderOptionActions(emptyList())
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
      cell(rightHeaderPanel)
        .resizableColumn()
        .align(AlignX.RIGHT)
        .customize(UnscaledGaps(left = 18))
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
    withFont(JBUI.Fonts.smallFont())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty()
    DialogUtil.registerMnemonic(this)
  }

  val modelSelectorLink = ActionLink(AgentPromptBundle.message("popup.generation.model.auto")).apply {
    autoHideOnDisable = false
    setDropDownLinkIcon()
    withFont(JBUI.Fonts.smallFont())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty()
    setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.model.tooltip")))
    accessibleContext.accessibleName = AgentPromptBundle.message("popup.generation.model.auto")
  }

  val reasoningEffortLink = ActionLink(AgentPromptBundle.message("popup.generation.reasoning.auto")).apply {
    autoHideOnDisable = false
    setDropDownLinkIcon()
    withFont(JBUI.Fonts.smallFont())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty()
    setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.reasoning.tooltip")))
    accessibleContext.accessibleName = AgentPromptBundle.message("popup.generation.reasoning.accessible.name")
  }

  val planReasoningEffortLink = ActionLink(AgentPromptBundle.message("popup.generation.plan.reasoning.same")).apply {
    autoHideOnDisable = false
    setDropDownLinkIcon()
    withFont(JBUI.Fonts.smallFont())
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.empty()
    setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.plan.reasoning.tooltip")))
    accessibleContext.accessibleName = AgentPromptBundle.message("popup.generation.plan.reasoning.accessible.name")
  }
  val defaultProfileActionControl = AgentPromptDefaultProfileActionControl()

  val contextChipsContainer = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(contextChipsPanel, BorderLayout.CENTER)
  }
  val composerContextActionsPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(2)
    add(addContextButton, BorderLayout.WEST)
  }
  val composerContextPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(4)
    addToTop(contextChipsContainer)
    addToBottom(composerContextActionsPanel)
  }

  val generationSettingsControlsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
    isOpaque = false
    add(modelSelectorLink)
    add(reasoningEffortLink)
    add(planReasoningEffortLink)
  }
  val generationSettingsPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(0, 6, 6, 6)
    add(generationSettingsControlsPanel, BorderLayout.WEST)
    add(defaultProfileActionControl.component, BorderLayout.EAST)
  }

  val promptEditorPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
    addToCenter(promptCardPanel)
    addToBottom(generationSettingsPanel)
  }

  val promptPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    border = JBUI.Borders.empty(6, 12, 8, 12)
    add(suggestionsPanel, BorderLayout.NORTH)
    add(promptEditorPanel, BorderLayout.CENTER)
    add(composerContextPanel, BorderLayout.SOUTH)
    minimumSize = PROMPT_PANEL_MINIMUM_SIZE
  }

  val statusStrip = AgentPromptStatusStrip(AgentPromptBundle.message("popup.footer.hint"))

  val bottomPanel = BorderLayoutPanel().apply {
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    addToCenter(existingTaskScrollPane)
    addToBottom(statusStrip.component)
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
    preferredSize = AGENT_PROMPT_PALETTE_PREFERRED_SIZE
    minimumSize = AGENT_PROMPT_PALETTE_MINIMUM_SIZE
    addToTop(headerPanel)
    addToCenter(promptPanel)
    addToBottom(bottomPanel)
  }
  headerToolbar.targetComponent = rootPanel
  headerControls.updateActions()

  WindowMoveListener(rootPanel).installTo(headerPanel)

  return AgentPromptPaletteView(
    rootPanel = rootPanel,
    promptPanel = promptPanel,
    promptEditorPanel = promptEditorPanel,
    suggestionsPanel = suggestionsPanel,
    composerContextPanel = composerContextPanel,
    bottomPanel = bottomPanel,
    tabbedPane = tabbedPane,
    profileAction = profileSelectorAction,
    promptLibraryIconLabel = promptLibraryIconLabel,
    generationSettingsPanel = generationSettingsPanel,
    launchProfileLink = launchProfileLink,
    modelSelectorLink = modelSelectorLink,
    reasoningEffortLink = reasoningEffortLink,
    planReasoningEffortLink = planReasoningEffortLink,
    defaultProfileActionControl = defaultProfileActionControl,
    addContextButton = addContextButton,
    existingTaskListModel = existingTaskListModel,
    existingTaskList = existingTaskList,
    existingTaskScrollPane = existingTaskScrollPane,
    statusStrip = statusStrip,
    rightHeaderPanel = rightHeaderPanel,
    headerToolbar = headerToolbar,
    headerControls = headerControls,
    containerModeAction = containerModeAction,
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
