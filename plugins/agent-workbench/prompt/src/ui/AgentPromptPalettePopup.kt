// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.dynatrace.hash4j.hashing.HashValue128
import com.intellij.AbstractBundle
import com.intellij.CommonBundle
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.context.dataContextOrNull
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.formatAgentSessionRelativeTimeShort
import com.intellij.agent.workbench.sessions.core.formatAgentSessionThreadTitle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchers
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.execution.ui.TagButton
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.ArrayDeque
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultEditorKit

private const val CONTEXT_SOFT_CAP_CHARS = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS
private const val MAX_EXISTING_TASKS = 200
private const val CONTEXT_CHIP_GAP = 6

internal fun buildPlainTextTooltipComponent(text: @Nls String): JBTextArea {
  return JBTextArea(text).apply {
    isEditable = false
    isFocusable = false
    lineWrap = false
    wrapStyleWord = false
    isOpaque = true
    background = UIUtil.getToolTipBackground()
    foreground = UIUtil.getToolTipForeground()
    font = UIUtil.getToolTipFont()
    border = JBUI.Borders.empty(4, 6)
  }
}

internal fun createPlainTextIdeTooltip(component: JComponent, textProvider: () -> @Nls String): IdeTooltip {
  return object : IdeTooltip(component, Point(0, 0), null, component) {
    init {
      layer = Balloon.Layer.top
      preferredPosition = Balloon.Position.above
    }

    override fun beforeShow(): Boolean {
      val text = textProvider().takeIf { it.isNotBlank() } ?: return false
      tipComponent = buildPlainTextTooltipComponent(text)
      return true
    }
  }
}

internal fun installPlainTextIdeTooltip(component: JComponent, textProvider: () -> @Nls String) {
  IdeTooltipManager.getInstance().setCustomTooltip(component, createPlainTextIdeTooltip(component, textProvider))
}

internal class AgentPromptPalettePopup(
  private val invocationData: AgentPromptInvocationData,
  private val providersProvider: () -> List<AgentSessionProviderBridge> = AgentSessionProviderBridges::allBridges,
  private val launcherProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
) {
  private val project: Project = invocationData.project
  private val contextResolverService: AgentPromptContextResolverService = project.service()
  private val uiStateService: AgentPromptUiSessionStateService = project.service()

  private val promptArea = JBTextArea(6, 100)
  private lateinit var tabbedPane: JBTabbedPane
  private lateinit var providerIconLabel: JBLabel

  private lateinit var existingTaskListModel: DefaultListModel<ThreadEntry>
  private lateinit var existingTaskList: JBList<ThreadEntry>
  private lateinit var existingTaskScrollPane: JBScrollPane

  private val contextChipsPanel = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(CONTEXT_CHIP_GAP), JBUI.scale(CONTEXT_CHIP_GAP))).apply {
    isOpaque = false
  }

  private lateinit var codexPlanModeCheckBox: JBCheckBox

  private lateinit var footerLabel: JBLabel

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private var clearDraftOnClose: Boolean = false
  private var canSubmitNow: Boolean = false
  private var contextEntries: List<ContextEntry> = emptyList()
  private var initialContextFingerprint: HashValue128? = null
  private val removedLogicalItemIds = LinkedHashSet<String>()
  private var providerEntries: List<ProviderEntry> = emptyList()
  private var providerMenuModel: AgentSessionProviderMenuModel = AgentSessionProviderMenuModel(emptyList(), emptyList())
  private var selectedProvider: ProviderEntry? = null
  private var allExistingTaskEntries: List<ThreadEntry> = emptyList()
  private var selectedExistingTaskId: String? = null
  private var selectedWorkingProjectPath: String? = null
  private var existingTaskSearchQuery: String = ""
  private val threadLoadVersion = AtomicInteger(0)
  @Suppress("RAW_SCOPE_CREATION")
  private val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)
  private var existingTasksObservationJob: Job? = null

  fun show() {
    promptArea.lineWrap = true
    promptArea.wrapStyleWord = true

    val content = createContentPanel()
    refreshProviders()
    loadInitialContext()

    restoreDraft()
    clearStatus()
    updateTargetModeUi()
    updateSendAvailability()

    val createdPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content, promptArea)
      .setProject(project)
      .setModalContext(false)
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelKeyEnabled(true)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, "AgentWorkbench.PromptPalette", true)
      .setLocateWithinScreenBounds(false)
      .createPopup()

    popup = createdPopup
    popupActive = true
    createdPopup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        popupActive = false
        popup = null
        existingTasksObservationJob?.cancel()
        existingTasksObservationJob = null
        popupScope.cancel("Agent prompt popup closed")
        if (clearDraftOnClose) {
          uiStateService.clearDraft()
        }
        else {
          saveDraft()
        }
      }
    })

    attachHandlers()
    createdPopup.showCenteredInCurrentWindow(project)
  }

  private fun createContentPanel(): JPanel {
    val planModeCheckBox = createCodexPlanModeCheckBox()
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      contextChipsPanel = contextChipsPanel,
      codexPlanModeCheckBox = planModeCheckBox,
      onProviderIconClicked = ::showProviderChooser,
      onExistingTaskSelected = ::onExistingTaskSelected,
    )
    tabbedPane = view.tabbedPane
    providerIconLabel = view.providerIconLabel
    existingTaskListModel = view.existingTaskListModel
    existingTaskList = view.existingTaskList
    existingTaskScrollPane = view.existingTaskScrollPane
    codexPlanModeCheckBox = checkNotNull(view.codexPlanModeCheckBox)
    footerLabel = view.footerLabel
    return view.rootPanel
  }

  private fun createCodexPlanModeCheckBox(): JBCheckBox {
    return JBCheckBox(AgentPromptBundle.message("popup.plan.checkbox"), true).apply {
      isFocusable = false
    }
  }

  private fun onExistingTaskSelected(selected: ThreadEntry) {
    selectedExistingTaskId = selected.id
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  private fun attachHandlers() {
    installPromptEnterHandlers()

    tabbedPane.addChangeListener(ChangeListener {
      updateTargetModeUi()
      updateSendAvailability()
      popup?.moveToFitScreen()
    })

    promptArea.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) = onPromptChanged()
      override fun removeUpdate(e: DocumentEvent?) = onPromptChanged()
      override fun changedUpdate(e: DocumentEvent?) = onPromptChanged()
    })
  }

  private fun onPromptChanged() {
    updateSendAvailability()
    // Clear transient footer messages on user interaction
    clearStatus()
  }

  private fun installPromptEnterHandlers() {
    installPromptEnterHandlers(
      promptArea = promptArea,
      canSubmit = { canSubmitNow },
      isTabQueueEnabled = {
        isTabQueueShortcutEnabled(
          targetMode = currentTargetMode(),
          selectedProvider = selectedProvider?.bridge?.provider,
        )
      },
      onSubmit = ::submit,
    )
  }

  private fun updateTargetModeUi() {
    val mode = currentTargetMode()
    // Prompt editor is shared across both modes; only the existing-task picker visibility changes.
    existingTaskScrollPane.isVisible = mode == PromptTargetMode.EXISTING_TASK
    if (mode == PromptTargetMode.EXISTING_TASK) {
      if (allExistingTaskEntries.isEmpty()) {
        reloadExistingTasks()
      }
    }
    refreshFooterHintForCurrentState()
  }

  private fun currentTargetMode(): PromptTargetMode {
    return if (tabbedPane.selectedIndex == 1) PromptTargetMode.EXISTING_TASK else PromptTargetMode.NEW_TASK
  }

  private fun setTargetMode(mode: PromptTargetMode) {
    tabbedPane.selectedIndex = when (mode) {
      PromptTargetMode.NEW_TASK -> 0
      PromptTargetMode.EXISTING_TASK -> 1
    }
  }

  private fun refreshProviders() {
    val bridges = providersProvider().sortedBy { providerPriority(it.provider) }
    providerMenuModel = buildAgentSessionProviderMenuModel(bridges)
    providerEntries = bridges.map { bridge ->
      ProviderEntry(
        bridge = bridge,
        displayName = resolveSessionsMessage(bridge.displayNameKey, bridge) ?: providerDisplayName(bridge.provider),
        isCliAvailable = bridge.isCliAvailable(),
        icon = providerIcon(bridge),
      )
    }

    val currentProviderId = selectedProvider?.bridge?.provider
    selectedProvider = providerEntries.firstOrNull { it.bridge.provider == currentProviderId }
      ?: providerEntries.firstOrNull { it.bridge.provider == AgentSessionProvider.CODEX }
      ?: providerEntries.firstOrNull()
    updateProviderIconPresentation()
  }

  private fun updateProviderIconPresentation() {
    val provider = selectedProvider
    if (provider == null) {
      providerIconLabel.icon = AllIcons.Toolwindows.ToolWindowMessages
      providerIconLabel.toolTipText = AgentPromptBundle.message("popup.provider.selector.tooltip")
      updateCodexPlanToggleVisibility()
      return
    }

    providerIconLabel.icon = provider.icon
    providerIconLabel.toolTipText = provider.displayName
    updateCodexPlanToggleVisibility()
  }

  private fun updateCodexPlanToggleVisibility() {
    codexPlanModeCheckBox.isVisible = selectedProvider?.bridge?.provider == AgentSessionProvider.CODEX
  }

  private fun showProviderChooser() {
    if (!providerMenuModel.hasEntries()) {
      showError(AgentPromptBundle.message("popup.error.no.providers"))
      return
    }

    val group = DefaultActionGroup()
    providerMenuModel.standardItems.forEach { item ->
      group.add(createProviderSelectionAction(item))
    }
    if (providerMenuModel.yoloItems.isNotEmpty()) {
      if (providerMenuModel.standardItems.isNotEmpty()) {
        group.add(Separator.getInstance())
      }
      val yoloSectionName = resolveSessionsMessage("toolwindow.action.new.session.section.auto")
        ?: AgentPromptBundle.message("popup.provider.section.auto")
      group.add(Separator.create(yoloSectionName))
      providerMenuModel.yoloItems.forEach { item ->
        group.add(createProviderSelectionAction(item))
      }
    }

    val chooserDataContext = invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(providerIconLabel)
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        group,
        chooserDataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(providerIconLabel)
  }

  private fun createProviderSelectionAction(item: AgentSessionProviderMenuItem): AnAction {
    val text = resolveSessionsMessage(item.labelKey, item.bridge) ?: item.displayNameFallback()
    return object : AnAction(text, null, providerIcon(item.bridge)) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = item.isEnabled
        e.presentation.description = if (item.isEnabled) null else disabledProviderReason(item)
      }

      override fun actionPerformed(e: AnActionEvent) {
        if (!item.isEnabled) {
          return
        }

        selectedProvider = providerEntries.firstOrNull { it.bridge.provider == item.bridge.provider }
        updateProviderIconPresentation()
        if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
          selectedExistingTaskId = null
          reloadExistingTasks()
        }
        updateSendAvailability()
        refreshFooterHintForCurrentState()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun AgentSessionProviderMenuItem.displayNameFallback(): @Nls String {
    return providerEntries.firstOrNull { it.bridge.provider == bridge.provider }?.displayName
      ?: providerDisplayName(bridge.provider)
  }

  private fun disabledProviderReason(item: AgentSessionProviderMenuItem): @Nls String {
    val reasonKey: @NonNls String? = item.disabledReasonKey
    if (reasonKey != null) {
      resolveSessionsMessage(reasonKey, item.bridge)?.let { resolved ->
        return resolved
      }
    }
    return AgentPromptBundle.message("popup.error.provider.unavailable", item.displayNameFallback())
  }

  private fun loadInitialContext() {
    val resolved = contextResolverService.collectDefaultContext(invocationData)
    val projectPath = resolveWorkingProjectPath(launcherProvider()) ?: project.basePath
    initialContextFingerprint = computeContextFingerprint(resolved)
    removedLogicalItemIds.clear()
    contextEntries = resolved.map { item -> ContextEntry(item = item, projectBasePath = projectPath) }
    rebuildContextChips()
  }

  private fun rebuildContextChips() {
    contextChipsPanel.removeAll()
    contextEntries.forEach { entry ->
      contextChipsPanel.add(createContextChip(entry))
    }
    contextChipsPanel.revalidate()
    contextChipsPanel.repaint()
  }

  private fun createContextChip(entry: ContextEntry): JComponent {
    return TagButton(entry.displayText) {
      val beforeEntries = contextEntries
      val updatedEntries = resolveContextEntriesAfterRemoval(beforeEntries, entry.id)
      removedLogicalItemIds.addAll(
        collectRemovedLogicalItemIds(
          beforeEntries = beforeEntries,
          afterEntries = updatedEntries,
        )
      )
      contextEntries = updatedEntries
      rebuildContextChips()
      showInfo(AgentPromptBundle.message("popup.status.context.removed"))
    }.apply {
      isOpaque = false
      isFocusable = false
      // TagButton's inner `styleTag` button is opaque by default; make it non-opaque and
      // transparent so only the rounded tag shape is painted (no rectangular tile behind it).
      components.filterIsInstance<JButton>()
        .filter { it.getClientProperty("styleTag") != null }
        .forEach {
          it.isOpaque = false
          it.background = UIUtil.TRANSPARENT_COLOR
          it.putClientProperty("JButton.backgroundColor", UIUtil.TRANSPARENT_COLOR)
          installPlainTextIdeTooltip(component = it) { entry.tooltipText }
        }
    }
  }

  private fun reloadExistingTasks() {
    existingTasksObservationJob?.cancel()
    existingTasksObservationJob = null

    val selectedProviderEntry = selectedProvider
    if (selectedProviderEntry == null) {
      updateExistingTaskListState(AgentPromptBundle.message("popup.error.no.providers"))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      updateSendAvailability()
      return
    }

    if (!selectedProviderEntry.isCliAvailable) {
      updateExistingTaskListState(AgentPromptBundle.message("popup.error.provider.unavailable", selectedProviderEntry.displayName))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      updateSendAvailability()
      return
    }

    val launcher = launcherProvider()
    if (launcher == null) {
      updateExistingTaskListState(AgentPromptBundle.message("popup.error.no.launcher"))
      selectedExistingTaskId = null
      updateSendAvailability()
      return
    }

    val projectPath = resolveWorkingProjectPath(launcher)
    if (projectPath == null) {
      updateExistingTaskListState(AgentPromptBundle.message("popup.error.project.path"))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      updateSendAvailability()
      return
    }

    updateExistingTaskListState(AgentPromptBundle.message("popup.existing.loading"))
    allExistingTaskEntries = emptyList()
    existingTaskListModel.clear()

    val requestVersion = threadLoadVersion.incrementAndGet()
    existingTasksObservationJob = popupScope.launch {
      launcher.observeExistingThreads(projectPath = projectPath, provider = selectedProviderEntry.bridge.provider)
        .onStart {
          launcher.refreshExistingThreads(projectPath = projectPath, provider = selectedProviderEntry.bridge.provider)
        }
        .catch {
          if (!popupActive || requestVersion != threadLoadVersion.get()) {
            return@catch
          }
          allExistingTaskEntries = emptyList()
          selectedExistingTaskId = null
          existingTaskListModel.clear()
          existingTaskList.emptyText.text = AgentPromptBundle.message("popup.existing.error")
          updateSendAvailability()
        }
        .collectLatest { snapshot ->
          if (!popupActive || requestVersion != threadLoadVersion.get()) {
            return@collectLatest
          }
          applyExistingTaskSnapshot(snapshot)
        }
    }
  }

  private fun applyExistingTaskSnapshot(snapshot: AgentPromptExistingThreadsSnapshot) {
    if (snapshot.hasError) {
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      existingTaskListModel.clear()
      existingTaskList.emptyText.text = AgentPromptBundle.message("popup.existing.error")
      updateSendAvailability()
      return
    }

    val loaded = formatExistingTaskEntries(snapshot)
    allExistingTaskEntries = loaded
    if (selectedExistingTaskId != null && loaded.none { it.id == selectedExistingTaskId }) {
      selectedExistingTaskId = null
    }
    existingTaskListModel.clear()
    loaded.forEach { existingTaskListModel.addElement(it) }
    if (loaded.isEmpty()) {
      existingTaskList.emptyText.text = when {
        snapshot.isLoading || !snapshot.hasLoaded -> AgentPromptBundle.message("popup.existing.loading")
        else -> AgentPromptBundle.message("popup.existing.empty")
      }
    }
    else {
      existingTaskList.emptyText.clear()
      val selectedIdx = loaded.indexOfFirst { it.id == selectedExistingTaskId }
      if (selectedIdx >= 0) {
        existingTaskList.selectedIndex = selectedIdx
      }
    }
    updateSendAvailability()
  }

  private fun formatExistingTaskEntries(snapshot: AgentPromptExistingThreadsSnapshot): List<ThreadEntry> {
    val now = System.currentTimeMillis()
    val nowLabel = resolveSessionsMessage("toolwindow.time.now") ?: AgentPromptBundle.message("popup.time.now")
    val unknownLabel = resolveSessionsMessage("toolwindow.time.unknown") ?: AgentPromptBundle.message("popup.time.unknown")
    return snapshot.threads
      .asSequence()
      .sortedByDescending { thread -> thread.updatedAt }
      .take(MAX_EXISTING_TASKS)
      .map { thread ->
        ThreadEntry(
          id = thread.id,
          displayText = formatAgentSessionThreadTitle(threadId = thread.id, title = thread.title) { idPrefix ->
            resolveSessionsMessage("toolwindow.thread.fallback.title", null, idPrefix)
              ?: AgentPromptBundle.message("popup.existing.fallback.title", idPrefix)
          },
          secondaryText = "  " + formatAgentSessionRelativeTimeShort(
            timestamp = thread.updatedAt,
            now = now,
            nowLabel = nowLabel,
            unknownLabel = unknownLabel,
          ),
          activity = thread.activity,
        )
      }
      .toList()
  }

  private fun updateExistingTaskListState(message: @Nls String) {
    existingTaskListModel.clear()
    existingTaskList.emptyText.text = message
  }

  private fun updateSendAvailability() {
    val selectedProviderEntry = selectedProvider
    val hasPrompt = promptArea.text.trim().isNotEmpty()
    val hasProjectPath = resolveWorkingProjectPath(launcherProvider()) != null
    val hasExistingTaskTarget = !selectedExistingTaskId.isNullOrBlank()

    val submitPrerequisitesMet = hasPrompt && hasProjectPath && selectedProviderEntry != null && selectedProviderEntry.isCliAvailable
    canSubmitNow = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> submitPrerequisitesMet
      PromptTargetMode.EXISTING_TASK -> submitPrerequisitesMet && hasExistingTaskTarget
    }
  }

  private fun submit() {
    val openedPopup = popup ?: return
    val selectedProviderEntry = selectedProvider
    val launcher = launcherProvider()
    val projectPath = resolveWorkingProjectPath(launcher)
    val validationErrorKey = resolveSubmitValidationErrorMessageKey(
      targetMode = currentTargetMode(),
      prompt = promptArea.text,
      selectedProvider = selectedProviderEntry?.bridge?.provider,
      isProviderCliAvailable = selectedProviderEntry?.isCliAvailable == true,
      hasProjectPath = projectPath != null,
      hasLauncher = launcher != null,
      selectedExistingTaskId = selectedExistingTaskId,
    )
    if (validationErrorKey != null) {
      if (validationErrorKey == "popup.error.project.path" &&
          launcher != null &&
          promptWorkingProjectPathSelection(launcher) { submit() }) {
        return
      }
      val message = if (validationErrorKey == "popup.error.provider.unavailable") {
        AgentPromptBundle.message(validationErrorKey, selectedProviderEntry?.displayName ?: "")
      }
      else {
        AgentPromptBundle.message(validationErrorKey)
      }
      showError(message)
      return
    }

    val prompt = promptArea.text.trim()
    val providerEntry = selectedProviderEntry ?: return
    val effectiveProjectPath = projectPath ?: return

    val selectedContextItems = contextEntries.map { it.item }
    val contextSelection = resolveContextSelection(selectedContextItems, effectiveProjectPath) ?: return
    val launcherBridge = launcher ?: return

    val targetThreadId = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> null
      PromptTargetMode.EXISTING_TASK -> selectedExistingTaskId ?: return
    }
    val effectiveCodexPlanModeEnabled = resolveEffectiveCodexPlanModeEnabled(
      selectedProvider = providerEntry.bridge.provider,
      isCodexPlanModeSelected = codexPlanModeCheckBox.isSelected,
      targetMode = currentTargetMode(),
      selectedThreadActivity = selectedExistingTaskEntry()?.activity,
    )

    val request = AgentPromptLaunchRequest(
      provider = providerEntry.bridge.provider,
      projectPath = effectiveProjectPath,
      launchMode = AgentSessionLaunchMode.STANDARD,
      initialMessageRequest = AgentPromptInitialMessageRequest(
        prompt = prompt,
        projectPath = effectiveProjectPath,
        contextItems = contextSelection.items,
        contextEnvelopeSummary = contextSelection.summary,
        codexPlanModeEnabled = effectiveCodexPlanModeEnabled,
      ),
      targetThreadId = targetThreadId,
      preferredDedicatedFrame = null,
    )

    val result = launcherBridge.launch(request)
    if (result.launched) {
      clearDraftOnClose = true
      openedPopup.cancel()
      return
    }

    val errorMessage = when (result.error) {
      AgentPromptLaunchError.PROVIDER_UNAVAILABLE -> AgentPromptBundle.message("popup.error.launch.provider")
      AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE -> AgentPromptBundle.message("popup.error.launch.mode")
      AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND -> AgentPromptBundle.message("popup.error.launch.thread.not.found")
      AgentPromptLaunchError.INTERNAL_ERROR -> AgentPromptBundle.message("popup.error.launch.internal")
      null -> AgentPromptBundle.message("popup.error.launch.internal")
    }
    showError(errorMessage)
  }

  private fun resolveContextSelection(items: List<AgentPromptContextItem>, projectPath: String?): ContextSelection? {
    val baseSummary = AgentPromptContextEnvelopeSummary(
      softCapChars = CONTEXT_SOFT_CAP_CHARS,
      softCapExceeded = false,
      autoTrimApplied = false,
    )
    if (items.isEmpty()) {
      return ContextSelection(items = emptyList(), summary = baseSummary)
    }

    val normalizedItems = items.map { item -> item.copy(body = item.body.trim()) }
    val serializedChars = AgentPromptContextEnvelopeFormatter.measureContextBlockChars(
      items = normalizedItems,
      summary = baseSummary,
      projectPath = projectPath,
    )
    if (serializedChars <= CONTEXT_SOFT_CAP_CHARS) {
      return ContextSelection(items = normalizedItems, summary = baseSummary)
    }

    val choice = Messages.showDialog(
      project,
      AgentPromptBundle.message("popup.context.softcap.message", serializedChars, CONTEXT_SOFT_CAP_CHARS),
      AgentPromptBundle.message("popup.context.softcap.title"),
      arrayOf(
        AgentPromptBundle.message("popup.context.softcap.action.send.full"),
        AgentPromptBundle.message("popup.context.softcap.action.auto.trim"),
        CommonBundle.getCancelButtonText(),
      ),
      0,
      Messages.getWarningIcon(),
    )

    return when (choice) {
      0 -> ContextSelection(
        items = normalizedItems,
        summary = AgentPromptContextEnvelopeSummary(
          softCapChars = CONTEXT_SOFT_CAP_CHARS,
          softCapExceeded = true,
          autoTrimApplied = false,
        ),
      )
      1 -> {
        val trimResult = AgentPromptContextEnvelopeFormatter.applySoftCap(
          items = normalizedItems,
          softCapChars = CONTEXT_SOFT_CAP_CHARS,
          projectPath = projectPath,
        )
        ContextSelection(
          items = trimResult.items,
          summary = AgentPromptContextEnvelopeSummary(
            softCapChars = CONTEXT_SOFT_CAP_CHARS,
            softCapExceeded = true,
            autoTrimApplied = true,
          ),
        )
      }
      else -> null
    }
  }

  private fun resolveWorkingProjectPath(launcher: AgentPromptLauncherBridge?): String? {
    selectedWorkingProjectPath?.takeIf { path -> path.isNotBlank() }?.let { path ->
      return path
    }
    return launcher
      ?.resolveWorkingProjectPath(invocationData)
      ?.takeIf { path -> path.isNotBlank() }
  }

  private fun promptWorkingProjectPathSelection(launcher: AgentPromptLauncherBridge, onSelected: () -> Unit): Boolean {
    val candidates = launcher.listWorkingProjectPathCandidates(invocationData)
      .asSequence()
      .filter { candidate -> candidate.path.isNotBlank() }
      .distinctBy { candidate -> candidate.path }
      .toList()
    if (candidates.isEmpty()) {
      return false
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(candidates)
      .setTitle(AgentPromptBundle.message("popup.project.chooser.title"))
      .setRenderer(object : ColoredListCellRenderer<AgentPromptProjectPathCandidate>() {
        override fun customizeCellRenderer(
          list: JList<out AgentPromptProjectPathCandidate>,
          value: AgentPromptProjectPathCandidate?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          if (value == null) {
            return
          }
          append(value.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          if (value.displayName != value.path) {
            append("  ${value.path}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
        }
      })
      .setItemChosenCallback { candidate ->
        selectedWorkingProjectPath = candidate.path
        if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
          selectedExistingTaskId = null
          reloadExistingTasks()
        }
        updateSendAvailability()
        onSelected()
      }
      .createPopup()
      .showInBestPositionFor(invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea))

    return true
  }

  private fun restoreDraft() {
    val draft = uiStateService.loadDraft()
    val contextRestoreSnapshot = uiStateService.loadContextRestoreSnapshot()
    val launcher = launcherProvider()

    promptArea.text = draft.promptText
    codexPlanModeCheckBox.isSelected = draft.codexPlanModeEnabled
    val persistedProvider = resolveRestoredPromptProvider(
      draftProviderId = draft.providerId,
      preferredProvider = launcher?.preferredProvider(),
      availableProviders = providerEntries.map { entry -> entry.bridge.provider },
    )
    selectedProvider = findProviderEntry(persistedProvider) ?: selectedProvider
    updateProviderIconPresentation()

    setTargetMode(draft.targetMode)
    existingTaskSearchQuery = draft.existingTaskSearch
    selectedExistingTaskId = draft.selectedExistingTaskId
    removedLogicalItemIds.clear()
    if (contextRestoreSnapshot.contextFingerprint == initialContextFingerprint) {
      val normalizedRemovedIds = normalizeRemovedContextItemIds(contextRestoreSnapshot.removedContextItemIds)
      removedLogicalItemIds.addAll(normalizedRemovedIds)
      val restoredEntries = applyDraftContextRemovals(
        entries = contextEntries,
        currentFingerprint = initialContextFingerprint,
        draftFingerprint = contextRestoreSnapshot.contextFingerprint,
        removedLogicalItemIds = normalizedRemovedIds,
      )
      if (restoredEntries != contextEntries) {
        contextEntries = restoredEntries
        rebuildContextChips()
      }
    }

    if (draft.targetMode == PromptTargetMode.EXISTING_TASK) {
      reloadExistingTasks()
    }
  }

  private fun selectedExistingTaskEntry(): ThreadEntry? {
    val selectedId = selectedExistingTaskId ?: return null
    return allExistingTaskEntries.firstOrNull { entry -> entry.id == selectedId }
  }

  private fun findProviderEntry(provider: AgentSessionProvider?): ProviderEntry? {
    if (provider == null) {
      return null
    }
    return providerEntries.firstOrNull { entry -> entry.bridge.provider == provider }
  }

  private fun saveDraft() {
    uiStateService.saveDraft(
      AgentPromptUiDraft(
        promptText = promptArea.text,
        providerId = selectedProvider?.bridge?.provider?.value,
        targetMode = currentTargetMode(),
        sendMode = PromptSendMode.SEND_NOW,
        existingTaskSearch = existingTaskSearchQuery,
        selectedExistingTaskId = selectedExistingTaskId,
        codexPlanModeEnabled = codexPlanModeCheckBox.isSelected,
      )
    )
    uiStateService.saveContextRestoreSnapshot(
      AgentPromptUiContextRestoreSnapshot(
        contextFingerprint = initialContextFingerprint,
        removedContextItemIds = normalizeRemovedContextItemIds(removedLogicalItemIds),
      )
    )
  }

  private fun providerDisplayName(provider: AgentSessionProvider): String {
    return when (provider) {
      AgentSessionProvider.CODEX -> AgentPromptBundle.message("provider.codex")
      AgentSessionProvider.CLAUDE -> AgentPromptBundle.message("provider.claude")
      else -> provider.value.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
      }
    }
  }

  private fun providerPriority(provider: AgentSessionProvider): Int {
    return when (provider) {
      AgentSessionProvider.CODEX -> 0
      AgentSessionProvider.CLAUDE -> 1
      else -> 2
    }
  }

  private fun providerIcon(bridge: AgentSessionProviderBridge): Icon {
    return bridge.icon
  }

  private fun resolveSessionsMessage(@NonNls key: String, bridge: AgentSessionProviderBridge? = null, vararg params: Any): @Nls String? {
    val classLoaders = linkedSetOf<ClassLoader>()
    bridge?.javaClass?.classLoader?.let(classLoaders::add)
    classLoaders.add(javaClass.classLoader)

    classLoaders.forEach { classLoader ->
      val bundle = runCatching {
        ResourceBundle.getBundle("messages.AgentSessionsBundle", Locale.getDefault(), classLoader)
      }.getOrNull() ?: return@forEach

      val resolved = AbstractBundle.messageOrNull(bundle, key, *params) ?: return@forEach
      return resolved
    }

    return null
  }

  private fun clearStatus() {
    footerLabel.text = AgentPromptBundle.message(
      resolveDefaultFooterHintMessageKey(
        targetMode = currentTargetMode(),
        selectedProvider = selectedProvider?.bridge?.provider,
      )
    )
    footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
  }

  private fun refreshFooterHintForCurrentState() {
    if (shouldShowExistingTaskSelectionHint(
        targetMode = currentTargetMode(),
        selectedExistingTaskId = selectedExistingTaskId,
        selectedProvider = selectedProvider?.bridge?.provider,
      )) {
      showInfo(AgentPromptBundle.message("popup.status.existing.select.task"))
      return
    }

    clearStatus()
  }

  private fun showError(message: @Nls String) {
    footerLabel.foreground = NamedColorUtil.getErrorForeground()
    footerLabel.text = message
  }

  private fun showInfo(message: @Nls String) {
    footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
    footerLabel.text = message
  }
}

internal fun resolveContextEntriesAfterRemoval(
  entries: List<ContextEntry>,
  removedEntryId: String,
): List<ContextEntry> {
  val removedEntry = entries.firstOrNull { entry -> entry.id == removedEntryId } ?: return entries
  val removedLogicalItemIds = removedEntry.logicalItemId
    ?.let { logicalItemId -> collectContextHierarchyRemovalIds(entries = entries, rootItemId = logicalItemId) }
    .orEmpty()
  return entries.filterNot { entry ->
    entry.id == removedEntryId || (entry.logicalItemId != null && entry.logicalItemId in removedLogicalItemIds)
  }
}

private fun collectContextHierarchyRemovalIds(
  entries: List<ContextEntry>,
  rootItemId: String,
): Set<String> {
  val queue = ArrayDeque<String>()
  val removedItemIds = LinkedHashSet<String>()
  queue.addLast(rootItemId)
  while (queue.isNotEmpty()) {
    val currentItemId = queue.removeFirst()
    if (!removedItemIds.add(currentItemId)) {
      continue
    }
    entries.forEach { entry ->
      val childItemId = entry.logicalItemId ?: return@forEach
      if (entry.logicalParentItemId == currentItemId && childItemId !in removedItemIds) {
        queue.addLast(childItemId)
      }
    }
  }
  return removedItemIds
}

private data class ContextSelection(
  @JvmField val items: List<AgentPromptContextItem>,
  @JvmField val summary: AgentPromptContextEnvelopeSummary,
)

internal fun resolveDefaultFooterHintMessageKey(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProvider?,
): @NonNls String {
  return if (isTabQueueShortcutEnabled(targetMode = targetMode, selectedProvider = selectedProvider)) {
    "popup.footer.hint.existing.codex"
  }
  else {
    "popup.footer.hint"
  }
}

internal fun isTabQueueShortcutEnabled(
  targetMode: PromptTargetMode,
  selectedProvider: AgentSessionProvider?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK && selectedProvider == AgentSessionProvider.CODEX
}

internal fun shouldShowExistingTaskSelectionHint(
  targetMode: PromptTargetMode,
  selectedExistingTaskId: String?,
  selectedProvider: AgentSessionProvider?,
): Boolean {
  return targetMode == PromptTargetMode.EXISTING_TASK &&
         selectedExistingTaskId.isNullOrBlank() &&
         selectedProvider != AgentSessionProvider.CODEX
}

internal fun resolveEffectiveCodexPlanModeEnabled(
  selectedProvider: AgentSessionProvider?,
  isCodexPlanModeSelected: Boolean,
  targetMode: PromptTargetMode,
  selectedThreadActivity: AgentThreadActivity?,
): Boolean {
  if (selectedProvider != AgentSessionProvider.CODEX || !isCodexPlanModeSelected) {
    return false
  }
  if (targetMode != PromptTargetMode.EXISTING_TASK) {
    return true
  }
  return selectedThreadActivity != AgentThreadActivity.PROCESSING && selectedThreadActivity != AgentThreadActivity.REVIEWING
}

internal fun resolveSubmitValidationErrorMessageKey(
  targetMode: PromptTargetMode,
  prompt: String,
  selectedProvider: AgentSessionProvider?,
  isProviderCliAvailable: Boolean,
  hasProjectPath: Boolean,
  hasLauncher: Boolean,
  selectedExistingTaskId: String?,
): @NonNls String? {
  if (prompt.trim().isEmpty()) {
    return "popup.error.empty.prompt"
  }
  if (selectedProvider == null) {
    return "popup.error.no.providers"
  }
  if (!isProviderCliAvailable) {
    return "popup.error.provider.unavailable"
  }
  if (!hasProjectPath) {
    return "popup.error.project.path"
  }
  if (!hasLauncher) {
    return "popup.error.no.launcher"
  }
  if (targetMode == PromptTargetMode.EXISTING_TASK && selectedExistingTaskId.isNullOrBlank()) {
    return "popup.error.existing.select.task"
  }
  return null
}

internal fun resolveRestoredPromptProvider(
  draftProviderId: String?,
  preferredProvider: AgentSessionProvider?,
  availableProviders: Iterable<AgentSessionProvider>,
): AgentSessionProvider? {
  val availableProviderSet = availableProviders.toSet()
  val draftProvider = draftProviderId
    ?.let(AgentSessionProvider::fromOrNull)
    ?.takeIf { provider -> provider in availableProviderSet }
  if (draftProvider != null) {
    return draftProvider
  }
  return preferredProvider?.takeIf { provider -> provider in availableProviderSet }
}

internal fun installPromptEnterHandlers(
  promptArea: JBTextArea,
  canSubmit: () -> Boolean,
  isTabQueueEnabled: () -> Boolean = { false },
  onSubmit: () -> Unit,
  onTabFocusTransfer: () -> Unit = promptArea::transferFocus,
  onTabBackwardFocusTransfer: () -> Unit = promptArea::transferFocusBackward,
) {
  val popupSubmitActionKey = "agent.prompt.submit"
  val popupNewLineActionKey = "agent.prompt.insert.break"
  val popupQueueActionKey = "agent.prompt.queue"
  val popupBackwardFocusActionKey = "agent.prompt.focus.backward"

  promptArea.focusTraversalKeysEnabled = false

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), popupSubmitActionKey)
  promptArea.actionMap.put(popupSubmitActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      canSubmit()
      onSubmit()
    }
  })

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), popupNewLineActionKey)
  promptArea.actionMap.put(popupNewLineActionKey, promptArea.actionMap.get(DefaultEditorKit.insertBreakAction))

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), popupQueueActionKey)
  promptArea.actionMap.put(popupQueueActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      if (isTabQueueEnabled()) {
        onSubmit()
      }
      else {
        onTabFocusTransfer()
      }
    }
  })

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), popupBackwardFocusActionKey)
  promptArea.actionMap.put(popupBackwardFocusActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      onTabBackwardFocusTransfer()
    }
  })
}
