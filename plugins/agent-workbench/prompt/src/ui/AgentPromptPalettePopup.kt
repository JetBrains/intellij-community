// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md

import com.dynatrace.hash4j.hashing.HashValue128
import com.intellij.CommonBundle
import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.context.dataContextOrNull
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchers
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.Nls
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private const val CONTEXT_SOFT_CAP_CHARS = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS

internal class AgentPromptPalettePopup(
  private val invocationData: AgentPromptInvocationData,
  private val providersProvider: () -> List<AgentSessionProviderBridge> = AgentSessionProviderBridges::allBridges,
  private val launcherProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
) {
  private val project: Project = invocationData.project
  private val contextResolverService: AgentPromptContextResolverService = project.service()
  private val uiStateService: AgentPromptUiSessionStateService = project.service()
  private val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPalettePopup::class.java.classLoader)

  private val promptArea = JBTextArea(6, 100)
  private val contextChips = AgentPromptContextChipsComponent(::removeContextEntry)

  private lateinit var tabbedPane: JBTabbedPane
  private lateinit var providerIconLabel: JBLabel
  private lateinit var existingTaskScrollPane: JBScrollPane
  private lateinit var codexPlanModeCheckBox: JBCheckBox
  private lateinit var footerLabel: JBLabel
  private lateinit var providerSelector: AgentPromptProviderSelector
  private lateinit var existingTaskController: AgentPromptExistingTaskController

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private var clearDraftOnClose: Boolean = false
  private var canSubmitNow: Boolean = false
  private var contextEntries: List<ContextEntry> = emptyList()
  private var initialContextFingerprint: HashValue128? = null
  private val removedLogicalItemIds = LinkedHashSet<String>()
  private var selectedWorkingProjectPath: String? = null
  private var existingTaskSearchQuery: String = ""

  @Suppress("RAW_SCOPE_CREATION")
  private val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

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
        existingTaskController.dispose()
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
      contextChipsPanel = contextChips.component,
      codexPlanModeCheckBox = planModeCheckBox,
      onProviderIconClicked = ::showProviderChooser,
      onExistingTaskSelected = ::onExistingTaskSelected,
    )
    tabbedPane = view.tabbedPane
    providerIconLabel = view.providerIconLabel
    existingTaskScrollPane = view.existingTaskScrollPane
    codexPlanModeCheckBox = checkNotNull(view.codexPlanModeCheckBox)
    footerLabel = view.footerLabel
    providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      providerIconLabel = providerIconLabel,
      codexPlanModeCheckBox = codexPlanModeCheckBox,
      providersProvider = providersProvider,
      sessionsMessageResolver = sessionsMessageResolver,
    )
    existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = view.existingTaskListModel,
      existingTaskList = view.existingTaskList,
      popupScope = popupScope,
      sessionsMessageResolver = sessionsMessageResolver,
      onStateChanged = ::updateSendAvailability,
    )
    return view.rootPanel
  }

  private fun createCodexPlanModeCheckBox(): JBCheckBox {
    return JBCheckBox(AgentPromptBundle.message("popup.plan.checkbox"), true).apply {
      isFocusable = false
    }
  }

  private fun onExistingTaskSelected(selected: ThreadEntry) {
    existingTaskController.onUserSelected(selected)
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  private fun attachHandlers() {
    installPromptEnterHandlers(
      promptArea = promptArea,
      canSubmit = { canSubmitNow },
      isTabQueueEnabled = {
        isTabQueueShortcutEnabled(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
        )
      },
      onSubmit = ::submit,
    )

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
    clearStatus()
  }

  private fun updateTargetModeUi() {
    val mode = currentTargetMode()
    existingTaskScrollPane.isVisible = mode == PromptTargetMode.EXISTING_TASK
    if (mode == PromptTargetMode.EXISTING_TASK && !existingTaskController.hasLoadedEntries()) {
      reloadExistingTasks()
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
    providerSelector.refresh()
  }

  private fun showProviderChooser() {
    providerSelector.showChooser(onUnavailable = ::showError) {
      if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
        existingTaskController.clearSelection()
        reloadExistingTasks()
      }
      updateSendAvailability()
      refreshFooterHintForCurrentState()
    }
  }

  private fun loadInitialContext() {
    val resolved = contextResolverService.collectDefaultContext(invocationData)
    val projectPath = resolveWorkingProjectPath(launcherProvider()) ?: project.basePath
    initialContextFingerprint = computeContextFingerprint(resolved)
    removedLogicalItemIds.clear()
    contextEntries = resolved.map { item -> ContextEntry(item = item, projectBasePath = projectPath) }
    contextChips.render(contextEntries)
  }

  private fun removeContextEntry(entry: ContextEntry) {
    val beforeEntries = contextEntries
    val updatedEntries = resolveContextEntriesAfterRemoval(beforeEntries, entry.id)
    removedLogicalItemIds.addAll(
      collectRemovedLogicalItemIds(
        beforeEntries = beforeEntries,
        afterEntries = updatedEntries,
      )
    )
    contextEntries = updatedEntries
    contextChips.render(contextEntries)
    showInfo(AgentPromptBundle.message("popup.status.context.removed"))
  }

  private fun reloadExistingTasks() {
    val launcher = launcherProvider()
    existingTaskController.reload(
      selectedProviderEntry = providerSelector.selectedProvider,
      launcher = launcher,
      projectPath = resolveWorkingProjectPath(launcher),
      isPopupActive = { popupActive },
    )
  }

  private fun updateSendAvailability() {
    val selectedProviderEntry = providerSelector.selectedProvider
    val hasPrompt = promptArea.text.trim().isNotEmpty()
    val hasProjectPath = resolveWorkingProjectPath(launcherProvider()) != null
    val hasExistingTaskTarget = !existingTaskController.selectedExistingTaskId.isNullOrBlank()

    val submitPrerequisitesMet = hasPrompt && hasProjectPath && selectedProviderEntry != null && selectedProviderEntry.isCliAvailable
    canSubmitNow = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> submitPrerequisitesMet
      PromptTargetMode.EXISTING_TASK -> submitPrerequisitesMet && hasExistingTaskTarget
    }
  }

  private fun submit() {
    val openedPopup = popup ?: return
    val selectedProviderEntry = providerSelector.selectedProvider
    val launcher = launcherProvider()
    val projectPath = resolveWorkingProjectPath(launcher)
    val validationErrorKey = resolveSubmitValidationErrorMessageKey(
      targetMode = currentTargetMode(),
      prompt = promptArea.text,
      selectedProvider = selectedProviderEntry?.bridge?.provider,
      isProviderCliAvailable = selectedProviderEntry?.isCliAvailable == true,
      hasProjectPath = projectPath != null,
      hasLauncher = launcher != null,
      selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
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

    val selectedContextItems = contextEntries.map(ContextEntry::item)
    val contextSelection = resolveContextSelection(selectedContextItems, effectiveProjectPath) ?: return
    val launcherBridge = launcher ?: return

    val targetThreadId = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> null
      PromptTargetMode.EXISTING_TASK -> existingTaskController.selectedExistingTaskId ?: return
    }
    val effectiveCodexPlanModeEnabled = resolveEffectiveCodexPlanModeEnabled(
      selectedProvider = providerEntry.bridge.provider,
      isCodexPlanModeSelected = codexPlanModeCheckBox.isSelected,
      targetMode = currentTargetMode(),
      selectedThreadActivity = existingTaskController.selectedEntry()?.activity,
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
      .distinctBy(AgentPromptProjectPathCandidate::path)
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
          existingTaskController.clearSelection()
          reloadExistingTasks()
        }
        updateSendAvailability()
        onSelected()
      }
      .createPopup()
      .showInBestPositionFor(invocationData.dataContextOrNull() ?: com.intellij.ide.DataManager.getInstance().getDataContext(promptArea))

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
      availableProviders = providerSelector.availableProviders,
    )
    providerSelector.selectProvider(persistedProvider)

    setTargetMode(draft.targetMode)
    existingTaskSearchQuery = draft.existingTaskSearch
    existingTaskController.selectedExistingTaskId = draft.selectedExistingTaskId
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
        contextChips.render(contextEntries)
      }
    }

    if (draft.targetMode == PromptTargetMode.EXISTING_TASK) {
      reloadExistingTasks()
    }
  }

  private fun saveDraft() {
    uiStateService.saveDraft(
      AgentPromptUiDraft(
        promptText = promptArea.text,
        providerId = providerSelector.selectedProvider?.bridge?.provider?.value,
        targetMode = currentTargetMode(),
        sendMode = PromptSendMode.SEND_NOW,
        existingTaskSearch = existingTaskSearchQuery,
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
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

  private fun clearStatus() {
    footerLabel.text = AgentPromptBundle.message(
      resolveDefaultFooterHintMessageKey(
        targetMode = currentTargetMode(),
        selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
      )
    )
    footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
  }

  private fun refreshFooterHintForCurrentState() {
    if (shouldShowExistingTaskSelectionHint(
        targetMode = currentTargetMode(),
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
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

private data class ContextSelection(
  @JvmField val items: List<AgentPromptContextItem>,
  @JvmField val summary: AgentPromptContextEnvelopeSummary,
)
