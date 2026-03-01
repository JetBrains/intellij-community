// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.intellij.AbstractBundle
import com.intellij.CommonBundle
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
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.execution.ui.TagButton
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
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
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultEditorKit

private const val CONTEXT_SOFT_CAP_CHARS = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS
private const val MAX_EXISTING_TASKS = 200
private const val CONTEXT_CHIP_GAP = 6

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

  private lateinit var footerLabel: JBLabel

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private var clearDraftOnClose: Boolean = false
  private var canSubmitNow: Boolean = false
  private var contextEntries: List<ContextEntry> = emptyList()
  private var providerEntries: List<ProviderEntry> = emptyList()
  private var providerMenuModel: AgentSessionProviderMenuModel = AgentSessionProviderMenuModel(emptyList(), emptyList())
  private var selectedProvider: ProviderEntry? = null
  private var allExistingTaskEntries: List<ThreadEntry> = emptyList()
  private var selectedExistingTaskId: String? = null
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
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      contextChipsPanel = contextChipsPanel,
      onProviderIconClicked = ::showProviderChooser,
      onExistingTaskSelected = ::onExistingTaskSelected,
    )
    tabbedPane = view.tabbedPane
    providerIconLabel = view.providerIconLabel
    existingTaskListModel = view.existingTaskListModel
    existingTaskList = view.existingTaskList
    existingTaskScrollPane = view.existingTaskScrollPane
    footerLabel = view.footerLabel
    return view.rootPanel
  }

  private fun onExistingTaskSelected(selected: ThreadEntry) {
    selectedExistingTaskId = selected.id
    updateSendAvailability()
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
    footerLabel.text = AgentPromptBundle.message("popup.footer.hint")
    footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
  }

  private fun installPromptEnterHandlers() {
    installPromptEnterHandlers(
      promptArea = promptArea,
      canSubmit = { canSubmitNow },
      targetMode = ::currentTargetMode,
      onSubmit = ::submit,
      onExistingTaskSubmitDisabled = {
        showInfo(AgentPromptBundle.message("popup.status.existing.select.task"))
      },
    )
  }

  private fun updateTargetModeUi() {
    val mode = currentTargetMode()
    // Prompt editor is shared across both modes; only the existing-task picker visibility changes.
    existingTaskScrollPane.isVisible = mode == PromptTargetMode.EXISTING_TASK
    val existingTaskHint = AgentPromptBundle.message("popup.status.existing.select.task")
    if (mode == PromptTargetMode.EXISTING_TASK) {
      if (allExistingTaskEntries.isEmpty()) {
        reloadExistingTasks()
      }
      if (selectedExistingTaskId == null) {
        showInfo(existingTaskHint)
      }
    }
    else if (footerLabel.text == existingTaskHint) {
      clearStatus()
    }
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
      return
    }

    providerIconLabel.icon = provider.icon
    providerIconLabel.toolTipText = provider.displayName
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
    contextEntries = resolved.map { item -> ContextEntry(item = item, projectBasePath = project.basePath) }
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
      contextEntries = contextEntries.filterNot { it.id == entry.id }
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
        }
      setToolTip(entry.tooltipText)
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

    val projectPath = project.basePath
    if (projectPath.isNullOrBlank()) {
      updateExistingTaskListState(AgentPromptBundle.message("popup.error.project.path"))
      allExistingTaskEntries = emptyList()
      selectedExistingTaskId = null
      updateSendAvailability()
      return
    }

    updateExistingTaskListState(AgentPromptBundle.message("popup.existing.loading"))
    allExistingTaskEntries = emptyList()
    existingTaskListModel.clear()

    val launcher = launcherProvider()
    if (launcher == null) {
      updateExistingTaskListState(AgentPromptBundle.message("popup.error.no.launcher"))
      selectedExistingTaskId = null
      updateSendAvailability()
      return
    }

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
    val hasProjectPath = !project.basePath.isNullOrBlank()
    val hasExistingTaskTarget = !selectedExistingTaskId.isNullOrBlank()

    val submitPrerequisitesMet = hasPrompt && hasProjectPath && selectedProviderEntry != null && selectedProviderEntry.isCliAvailable
    canSubmitNow = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> submitPrerequisitesMet
      PromptTargetMode.EXISTING_TASK -> submitPrerequisitesMet && hasExistingTaskTarget
    }
  }

  private fun submit() {
    val openedPopup = popup ?: return

    val prompt = promptArea.text.trim()
    if (prompt.isEmpty()) {
      showError(AgentPromptBundle.message("popup.error.empty.prompt"))
      return
    }

    val selectedProviderEntry = selectedProvider
    if (selectedProviderEntry == null) {
      showError(AgentPromptBundle.message("popup.error.no.providers"))
      return
    }
    if (!selectedProviderEntry.isCliAvailable) {
      showError(AgentPromptBundle.message("popup.error.provider.unavailable", selectedProviderEntry.displayName))
      return
    }

    val projectPath = project.basePath
    if (projectPath.isNullOrBlank()) {
      showError(AgentPromptBundle.message("popup.error.project.path"))
      return
    }

    val selectedContextItems = contextEntries.map { it.item }
    val contextSelection = resolveContextSelection(selectedContextItems) ?: return

    val launcher = launcherProvider()
    if (launcher == null) {
      showError(AgentPromptBundle.message("popup.error.no.launcher"))
      return
    }

    val targetThreadId = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> null
      PromptTargetMode.EXISTING_TASK -> selectedExistingTaskId ?: run {
        showInfo(AgentPromptBundle.message("popup.status.existing.select.task"))
        return
      }
    }

    val request = AgentPromptLaunchRequest(
      provider = selectedProviderEntry.bridge.provider,
      projectPath = projectPath,
      launchMode = AgentSessionLaunchMode.STANDARD,
      initialMessageRequest = AgentPromptInitialMessageRequest(
        prompt = prompt,
        projectPath = projectPath,
        contextItems = contextSelection.items,
        contextEnvelopeSummary = contextSelection.summary,
      ),
      targetThreadId = targetThreadId,
      preferredDedicatedFrame = null,
    )

    val result = launcher.launch(request)
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

  private fun resolveContextSelection(items: List<AgentPromptContextItem>): ContextSelection? {
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
      projectPath = project.basePath,
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
          projectPath = project.basePath,
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

  private fun restoreDraft() {
    val draft = uiStateService.loadDraft()

    promptArea.text = draft.promptText
    val persistedProvider = draft.providerId?.let(AgentSessionProvider::fromOrNull)
    selectedProvider = findProviderEntry(persistedProvider) ?: selectedProvider
    updateProviderIconPresentation()

    setTargetMode(draft.targetMode)
    existingTaskSearchQuery = draft.existingTaskSearch
    selectedExistingTaskId = draft.selectedExistingTaskId

    if (draft.targetMode == PromptTargetMode.EXISTING_TASK) {
      reloadExistingTasks()
    }
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
    footerLabel.text = AgentPromptBundle.message("popup.footer.hint")
    footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
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

private data class ContextSelection(
  @JvmField val items: List<AgentPromptContextItem>,
  @JvmField val summary: AgentPromptContextEnvelopeSummary,
)

internal fun installPromptEnterHandlers(
  promptArea: JBTextArea,
  canSubmit: () -> Boolean,
  targetMode: () -> PromptTargetMode,
  onSubmit: () -> Unit,
  onExistingTaskSubmitDisabled: () -> Unit,
) {
  val popupSubmitActionKey = "agent.prompt.submit"
  val popupNewLineActionKey = "agent.prompt.insert.break"

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), popupSubmitActionKey)
  promptArea.actionMap.put(popupSubmitActionKey, object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      if (canSubmit()) {
        onSubmit()
      }
      else if (targetMode() == PromptTargetMode.EXISTING_TASK) {
        onExistingTaskSubmitDisabled()
      }
    }
  })

  promptArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), popupNewLineActionKey)
  promptArea.actionMap.put(popupNewLineActionKey, promptArea.actionMap.get(DefaultEditorKit.insertBreakAction))
}
