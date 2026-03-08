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
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPaletteExtension
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPaletteExtensions
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
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
  private lateinit var planModeCheckBox: JBCheckBox
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
  private var selectedLaunchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD
  private var selectedWorkingProjectPath: String? = null
  private var existingTaskSearchQuery: String = ""
  private var activeExtensionTabs: List<ExtensionTabEntry> = emptyList()
  private var activeExtensionTab: ExtensionTabEntry? = null
  private var activeTaskKey: String? = null
  private val taskPromptTexts: MutableMap<String, String> = HashMap()

  @Suppress("RAW_SCOPE_CREATION")
  private val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

  private class ExtensionTabEntry(
    @JvmField val extension: AgentPromptPaletteExtension,
    @JvmField val tabPanel: JPanel,
    @JvmField val taskKey: String,
  )

  private fun resolveTaskKey(panel: JPanel?): String? {
    if (panel == null) return null
    val mode = panel.getClientProperty("targetMode") as? PromptTargetMode
    if (mode != null) return mode.name
    return activeExtensionTabs.firstOrNull { it.tabPanel === panel }?.taskKey
  }

  fun show() {
    promptArea.lineWrap = true
    promptArea.wrapStyleWord = true

    val content = createContentPanel()
    refreshProviders()
    loadInitialContext()
    resolveExtensionTabs()

    val draft = restoreDraft()
    activeTaskKey = resolveTaskKey(tabbedPane.selectedComponent as? JPanel)
    restoreTaskDrafts(draft)
    if (activeExtensionTabs.isNotEmpty()) {
      selectFirstExtensionTab()
    }
    loadPromptTextForSelectedTab()
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
    val planModeCheckBox = createPlanModeCheckBox()
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      contextChipsPanel = contextChips.component,
      planModeCheckBox = planModeCheckBox,
      onProviderIconClicked = ::showProviderChooser,
      onExistingTaskSelected = ::onExistingTaskSelected,
    )
    tabbedPane = view.tabbedPane
    providerIconLabel = view.providerIconLabel
    existingTaskScrollPane = view.existingTaskScrollPane
    this.planModeCheckBox = checkNotNull(view.planModeCheckBox)
    footerLabel = view.footerLabel
    providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      providerIconLabel = providerIconLabel,
      codexPlanModeCheckBox = planModeCheckBox,
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

  private fun createPlanModeCheckBox(): JBCheckBox {
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
      handleTabSwitch()
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
    val isExtensionTab = activeExtensionTab != null
    val mode = currentTargetMode()
    existingTaskScrollPane.isVisible = !isExtensionTab && mode == PromptTargetMode.EXISTING_TASK
    if (!isExtensionTab && mode == PromptTargetMode.EXISTING_TASK && !existingTaskController.hasLoadedEntries()) {
      reloadExistingTasks()
    }
    updatePlanToggleVisibility()
    refreshFooterHintForCurrentState()
  }

  private fun currentTargetMode(): PromptTargetMode {
    val selectedComponent = tabbedPane.selectedComponent as? JPanel ?: return PromptTargetMode.NEW_TASK
    return selectedComponent.getClientProperty("targetMode") as? PromptTargetMode ?: PromptTargetMode.NEW_TASK
  }

  private fun setTargetMode(mode: PromptTargetMode) {
    val index = findTabIndexForMode(mode) ?: return
    tabbedPane.selectedIndex = index
  }

  private fun findTabIndexForMode(mode: PromptTargetMode): Int? {
    for (i in 0 until tabbedPane.tabCount) {
      val component = tabbedPane.getComponentAt(i) as? JPanel ?: continue
      if (component.getClientProperty("targetMode") == mode) {
        return i
      }
    }
    return null
  }

  private fun refreshProviders() {
    providerSelector.refresh()
  }

  private fun updatePlanToggleVisibility() {
    planModeCheckBox.isVisible = activeExtensionTab == null && providerSelector.selectedProvider?.bridge?.supportsPlanMode == true
  }

  private fun showProviderChooser() {
    providerSelector.showChooser(onUnavailable = ::showError) {
      if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
        existingTaskController.clearSelection()
        reloadExistingTasks()
      }
      updatePlanToggleVisibility()
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

  private fun resolveExtensionTabs() {
    val items = contextEntries.map { it.item }
    val matchingExtensions = AgentPromptPaletteExtensions.allExtensions().filter { it.matches(items) }

    // Remove tabs for extensions that no longer match
    val toRemove = activeExtensionTabs.filter { entry -> matchingExtensions.none { it === entry.extension } }
    toRemove.forEach { removeExtensionTab(it) }

    // Add tabs for newly matching extensions
    val existingExtensions = activeExtensionTabs.map { it.extension }.toSet()
    matchingExtensions.filter { it !in existingExtensions }.forEach { addExtensionTab(it) }

    // Update activeExtensionTab reference
    val selectedComponent = tabbedPane.selectedComponent as? JPanel
    activeExtensionTab = activeExtensionTabs.firstOrNull { it.tabPanel === selectedComponent }
  }

  private fun addExtensionTab(extension: AgentPromptPaletteExtension) {
    val panel = JPanel()
    val key = "extension:" + extension.javaClass.name
    tabbedPane.addTab(extension.getTabTitle(), panel)
    activeExtensionTabs = activeExtensionTabs + ExtensionTabEntry(extension = extension, tabPanel = panel, taskKey = key)
  }

  private fun removeExtensionTab(entry: ExtensionTabEntry) {
    val index = (0 until tabbedPane.tabCount).firstOrNull { tabbedPane.getComponentAt(it) === entry.tabPanel }
    if (index != null) {
      tabbedPane.removeTabAt(index)
    }
    activeExtensionTabs = activeExtensionTabs.filter { it !== entry }
    taskPromptTexts.remove(entry.taskKey)
    if (activeExtensionTab === entry) {
      activeExtensionTab = null
      setTargetMode(PromptTargetMode.NEW_TASK)
    }
  }

  private fun selectFirstExtensionTab() {
    val firstExtension = activeExtensionTabs.firstOrNull() ?: return
    val index = (0 until tabbedPane.tabCount).firstOrNull { tabbedPane.getComponentAt(it) === firstExtension.tabPanel }
    if (index != null) {
      tabbedPane.selectedIndex = index
    }
  }

  private fun restoreTaskDrafts(draft: AgentPromptUiDraft) {
    val savedDrafts = draft.taskDrafts

    // Built-in tabs: restore from taskDrafts, fall back to legacy promptText for NEW_TASK
    val newTaskKey = PromptTargetMode.NEW_TASK.name
    taskPromptTexts[newTaskKey] = savedDrafts[newTaskKey] ?: draft.promptText
    val existingTaskKey = PromptTargetMode.EXISTING_TASK.name
    savedDrafts[existingTaskKey]?.let { taskPromptTexts[existingTaskKey] = it }

    // Extension tabs: restore from taskDrafts, fall back to extension's initial text
    for (entry in activeExtensionTabs) {
      val savedText = savedDrafts[entry.taskKey]
      if (savedText != null) {
        taskPromptTexts[entry.taskKey] = savedText
      }
      else {
        val initialText = entry.extension.getInitialPromptText(project)
        if (!initialText.isNullOrBlank()) {
          taskPromptTexts[entry.taskKey] = initialText
        }
      }
    }
  }

  private fun handleTabSwitch() {
    savePromptTextForSelectedTab()
    loadPromptTextForSelectedTab()
  }

  private fun savePromptTextForSelectedTab() {
    val key = activeTaskKey ?: return
    taskPromptTexts[key] = promptArea.text
  }

  private fun loadPromptTextForSelectedTab() {
    val newPanel = tabbedPane.selectedComponent as? JPanel
    val newKey = resolveTaskKey(newPanel)
    activeTaskKey = newKey
    activeExtensionTab = newPanel?.let { comp -> activeExtensionTabs.firstOrNull { it.tabPanel === comp } }
    promptArea.text = newKey?.let { taskPromptTexts[it] } ?: ""
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
    resolveExtensionTabs()
    updateTargetModeUi()
    updateSendAvailability()
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

    if (activeExtensionTab != null) {
      canSubmitNow = true
      return
    }

    val submitPrerequisitesMet = hasPrompt && hasProjectPath && selectedProviderEntry != null && selectedProviderEntry.isCliAvailable
    canSubmitNow = when (currentTargetMode()) {
      PromptTargetMode.NEW_TASK -> submitPrerequisitesMet
      PromptTargetMode.EXISTING_TASK -> submitPrerequisitesMet && hasExistingTaskTarget
    }
  }

  private fun submit() {
    val openedPopup = popup ?: return

    val extensionTab = activeExtensionTab
    if (extensionTab != null) {
      val actionId = extensionTab.extension.getSubmitActionId()
      if (actionId != null) {
        val action = ActionManager.getInstance().getAction(actionId)
        if (action != null) {
          val dataContext = invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea)
          val event = AnActionEvent.createEvent(action, dataContext, null, invocationData.actionPlace ?: "", ActionUiKind.NONE, null)
          action.actionPerformed(event)
          clearDraftOnClose = true
          openedPopup.cancel()
          return
        }
      }
    }

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

    val targetThreadId = if (activeExtensionTab != null) {
      null
    }
    else {
      when (currentTargetMode()) {
        PromptTargetMode.NEW_TASK -> null
        PromptTargetMode.EXISTING_TASK -> existingTaskController.selectedExistingTaskId ?: return
      }
    }
    val effectivePlanModeEnabled = if (activeExtensionTab != null) {
      false
    }
    else {
      resolveEffectivePlanModeEnabled(
        supportsPlanMode = providerEntry.bridge.supportsPlanMode,
        isPlanModeSelected = planModeCheckBox.isSelected,
        targetMode = currentTargetMode(),
        selectedThreadActivity = existingTaskController.selectedEntry()?.activity,
      )
    }

    val request = AgentPromptLaunchRequest(
      provider = providerEntry.bridge.provider,
      projectPath = effectiveProjectPath,
      launchMode = selectedLaunchMode,
      initialMessageRequest = AgentPromptInitialMessageRequest(
        prompt = prompt,
        projectPath = effectiveProjectPath,
        contextItems = contextSelection.items,
        contextEnvelopeSummary = contextSelection.summary,
        planModeEnabled = effectivePlanModeEnabled,
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
      .showInBestPositionFor(invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea))

    return true
  }

  private fun restoreDraft(): AgentPromptUiDraft {
    val draft = uiStateService.loadDraft()
    val contextRestoreSnapshot = uiStateService.loadContextRestoreSnapshot()
    val launcher = launcherProvider()

    planModeCheckBox.isSelected = draft.planModeEnabled
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

    return draft
  }

  private fun saveDraft() {
    savePromptTextForSelectedTab()

    // Persist all tab texts so they survive popup close/reopen.
    val allTaskDrafts = HashMap(taskPromptTexts)

    uiStateService.saveDraft(
      AgentPromptUiDraft(
        promptText = allTaskDrafts[PromptTargetMode.NEW_TASK.name] ?: "",
        providerId = providerSelector.selectedProvider?.bridge?.provider?.value,
        targetMode = currentTargetMode(),
        sendMode = PromptSendMode.SEND_NOW,
        existingTaskSearch = existingTaskSearchQuery,
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        planModeEnabled = planModeCheckBox.isSelected,
        taskDrafts = allTaskDrafts,
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
    val extensionTab = activeExtensionTab
    footerLabel.text = if (extensionTab != null) {
      extensionTab.extension.getFooterHint() ?: AgentPromptBundle.message("popup.footer.hint.default.tab")
    }
    else {
      AgentPromptBundle.message(
        resolveDefaultFooterHintMessageKey(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
        )
      )
    }
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
