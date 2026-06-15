// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INITIAL_TEXT_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptContainerLauncher
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtensionContext
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.IdeFocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.ChangeListener

private const val CODEX_SKILL_PREFIX = '$'

internal class AgentPromptPaletteSessionController(
  private val project: Project,
  private val invocationData: AgentPromptInvocationData,
  private val promptArea: AgentPromptTextField,
  private val view: AgentPromptPaletteView,
  private val contextChips: AgentPromptContextChipsComponent,
  private val providerSelector: AgentPromptProviderSelector,
  private val existingTaskController: AgentPromptExistingTaskController,
  private val suggestionController: AgentPromptSuggestionController,
  private val contextResolverService: AgentPromptContextResolverService,
  private val uiStateService: AgentPromptUiSessionStateService,
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val closePopup: () -> Unit,
  private val isPopupActive: () -> Boolean,
  private val movePopupToFitScreen: () -> Unit,
  private val popupScope: CoroutineScope,
  private val parentDisposable: Disposable,
) {
  private val contextState = AgentPromptPaletteContextState()
  private val draftState = AgentPromptPaletteDraftState()
  private val launchState = AgentPromptPaletteLaunchState()

  private val contextController: AgentPromptPaletteContextController
  private val draftController: AgentPromptPaletteDraftController
  private val generationSettingsController: AgentPromptGenerationSettingsController
  private val submitController: AgentPromptPaletteSubmitController

  @Volatile
  private var codexSkillCompletionEntries: List<AgentPromptReusableSourceEntry> = emptyList()

  init {
    lateinit var submitControllerRef: AgentPromptPaletteSubmitController

    contextController = AgentPromptPaletteContextController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      view = view,
      parentDisposable = parentDisposable,
      contextResolverService = contextResolverService,
      contextChips = contextChips,
      launcherProvider = launcherProvider,
      state = contextState,
      resolveWorkingProjectPath = { submitControllerRef.resolveWorkingProjectPath() },
      resolveContextProjectBasePath = { submitControllerRef.resolveContextProjectBasePath() },
      showError = ::showError,
      onContextChanged = ::handleContextChanged,
      onExtensionTabRemoved = { taskKeyPrefix ->
        draftState.taskPromptStates.keys.removeAll { key ->
          AgentPromptExtensionDraftDecisions.matchesTaskKey(taskKeyPrefix, key)
        }
      },
      setTargetMode = ::setTargetMode,
    )

    draftController = AgentPromptPaletteDraftController(
      invocationData = invocationData,
      promptArea = promptArea,
      tabbedPane = view.tabbedPane,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      uiStateService = uiStateService,
      launcherProvider = launcherProvider,
      contextState = contextState,
      draftState = draftState,
      refreshContextEntries = contextController::refreshContextEntries,
      resolveExtensionTabs = contextController::resolveExtensionTabs,
      reloadExistingTasks = ::reloadExistingTasks,
      updateProviderOptionsVisibility = ::updateProviderOptionsVisibility,
      setTargetMode = ::setTargetMode,
      resolveTaskKey = ::resolveTaskKey,
      getContainerModeSelected = ::isContainerModeSelectedForCurrentState,
      restoreContainerModeSelection = ::syncContainerModeState,
    )
    generationSettingsController = AgentPromptGenerationSettingsController(
      invocationData = invocationData,
      providerSelector = providerSelector,
      generationSettingsPanel = view.generationSettingsPanel,
      profileAction = view.profileAction,
      launchProfileLink = view.launchProfileLink,
      modelSelectorLink = view.modelSelectorLink,
      reasoningEffortLink = view.reasoningEffortLink,
      planReasoningEffortLink = view.planReasoningEffortLink,
      defaultProfileActionControl = view.defaultProfileActionControl,
      modelCatalogScope = popupScope,
      launcherProvider = launcherProvider,
      onDefaultSaved = ::showInfo,
      onLaunchProfileApplied = {
        updateProviderOptionsVisibility()
        updateSendAvailability()
        refreshFooterHintForCurrentState()
      },
      manageProfilesDialogRunner = ::runManageProfilesDialog,
    )
    submitController = AgentPromptPaletteSubmitController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      launcherProvider = launcherProvider,
      launchState = launchState,
      currentTargetMode = ::currentTargetMode,
      activeExtensionTab = { contextState.activeExtensionTab },
      buildVisibleContextEntries = { contextController.buildVisibleContextEntries() },
      resolveContextSelection = contextController::resolveContextSelection,
      onWorkingProjectPathSelected = { _ -> handleWorkingProjectPathSelected() },
      onSubmitBlocked = ::showError,
      onSubmitSucceeded = ::closeAfterSuccessfulSubmit,
      onPromptSubmitted = uiStateService::saveSubmittedPromptHistoryEntry,
      generationSettingsProvider = generationSettingsController::currentLaunchSettings,
      generationModelCatalogProvider = generationSettingsController::currentGenerationModelCatalog,
      isContainerModeSelected = ::isContainerModeSelectedForCurrentState,
      isContainerModeSupported = ::isContainerModeSupported,
      isContainerModeRuntimeAvailable = ::isContainerModeRuntimeAvailable,
    )
    submitControllerRef = submitController
  }

  fun initialize(initialAddContextRequest: AgentPromptAddContextRequest? = null) {
    contextController.configureAddContextButton()
    refreshProviders()
    contextController.loadInitialContext(initialAddContextRequest?.contextItems)
    contextController.resolveExtensionTabs()

    val draft = draftController.restoreDraft(restoreContextSnapshot = initialAddContextRequest == null)
    draftController.restoreTaskDrafts(draft)
    generationSettingsController.restoreLaunchProfiles(
      launcherProvider()?.loadProviderPreferences() ?: AgentPromptLauncherBridge.ProviderPreferences()
    )
    refreshExtensionTaskDraftsFromContext()

    if (invocationData.attributes[com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY] == true) {
      contextController.selectAutoSelectExtensionTab()
    }
    contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    val initialText = invocationData.dataContextOrNull()?.getData(AGENT_PROMPT_INITIAL_TEXT_DATA_KEY)
    draftController.overrideInitialTextIfProvided(initialText)
    if (initialAddContextRequest != null) {
      applyInitialAddContextTarget(initialAddContextRequest.target)
      generationSettingsController.refreshPresentation()
      contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    }
    draftController.loadPromptTextForSelectedTab()
    clearStatus()
    updateTargetModeUi()
    updateSendAvailability()
  }

  fun installHandlers() {
    contextController.installImagePasteHandler()
    contextController.installImageDropHandler()

    installConfirmActionOnEnter(view.existingTaskList) {
      submitController.submit()
      true
    }

    installPromptEnterHandlers(
      promptArea = promptArea,
      canSubmit = submitController::canSubmit,
      isTabQueueEnabled = {
        isTabQueueShortcutEnabled(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge,
          hasNextPromptTab = view.tabbedPane.selectedIndex in 0 until view.tabbedPane.tabCount - 1,
        )
      },
      onSubmit = submitController::submit,
      onTabFocusTransfer = { selectAdjacentPromptTab(view.tabbedPane, 1) },
      onTabBackwardFocusTransfer = { selectAdjacentPromptTab(view.tabbedPane, -1) },
    )

    view.tabbedPane.addChangeListener(ChangeListener {
      handleTabSwitch()
      updateTargetModeUi()
      updateSendAvailability()
      movePopupToFitScreen()
    })

    promptArea.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        onPromptChanged()
        autoPopupCommandCompletionIfNeeded(event)
      }
    })
  }

  fun onPopupClosed() {
    existingTaskController.dispose()
    suggestionController.dispose()
    draftController.saveProviderPreferences()
    if (launchState.clearDraftOnClose) {
      uiStateService.clearDraft()
    }
    else {
      draftController.saveDraft(currentTargetMode())
    }
  }

  fun showPromptLibraryChooser() {
    popupScope.launch {
      val sourceEntries = collectReusablePromptSourceEntries(
        workingProjectPaths = reusableSourceProjectPaths(),
      )
      val historyEntries = uiStateService.loadPromptHistory()
      withContext(Dispatchers.EDT) {
        showPromptLibraryChooser(
          promptFiles = sourceEntries,
          historyEntries = historyEntries,
        )
      }
    }
  }

  val isPinned: Boolean
    get() = !uiStateService.autoClose

  fun togglePin() {
    uiStateService.autoClose = !uiStateService.autoClose
  }

  fun onExistingTaskSelected(selected: ThreadEntry) {
    existingTaskController.onUserSelected(selected)
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  fun onExistingTaskStateChanged() {
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  fun onProviderOptionsChanged() {
    generationSettingsController.refreshPresentation()
    updateSendAvailability()
  }

  fun onProviderSelectionChanged() {
    generationSettingsController.refreshPresentation()
    updateProviderOptionsVisibility()
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  fun removeContextEntry(entry: ContextEntry) {
    contextController.removeContextEntry(entry)
  }

  fun applySuggestedPrompt(candidate: AgentPromptSuggestionCandidate) {
    draftController.applySuggestedPrompt(candidate.promptText)
    IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
  }

  fun codexSkillCompletionEntriesForCompletion(): List<AgentPromptReusableSourceEntry> = codexSkillCompletionEntries

  fun resolveWorkingProjectPath(): String? = submitController.resolveWorkingProjectPath()

  fun applyAddContextRequest(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult {
    val result = contextController.addExternalContextItems(request.contextItems)
    when (result) {
      AgentPromptAddContextApplyResult.ADDED -> handleContextChanged(AgentPromptBundle.message("popup.status.context.added"))
      AgentPromptAddContextApplyResult.ALREADY_ADDED -> showInfo(AgentPromptBundle.message("popup.status.context.already.added"))
    }
    return result
  }

  private fun handleTabSwitch() {
    draftController.savePromptTextForSelectedTab()
    contextController.syncActiveExtensionTab(view.tabbedPane.selectedComponent as? JPanel)
    draftController.loadPromptTextForSelectedTab()
  }

  private fun showPromptLibraryChooser(
    promptFiles: List<AgentPromptReusableSourceEntry>,
    historyEntries: List<AgentPromptHistoryEntry>,
  ) {
    val snapshot = draftController.snapshotPrompt()
    val promptLibraryState = PromptLibraryState(
      promptFiles = promptFiles,
      savedPromptEntries = uiStateService.loadSavedPrompts(),
      historyEntries = historyEntries,
    )
    var promptLibraryPopup: JBPopup? = null
    val popupsWithoutPromptRestore = mutableSetOf<JBPopup>()
    lateinit var openPromptLibraryPopup: (String?) -> Unit

    fun refreshPromptLibraryPopup(preselectNormalizedPromptText: String?) {
      val popupToRefresh = promptLibraryPopup ?: return
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed || promptLibraryPopup !== popupToRefresh || popupToRefresh.isDisposed) {
          return@invokeLater
        }
        popupsWithoutPromptRestore.add(popupToRefresh)
        popupToRefresh.cancel()
        promptLibraryPopup = null
        openPromptLibraryPopup(preselectNormalizedPromptText)
      }
    }

    openPromptLibraryPopup = open@{ preselectNormalizedPromptText ->
      val rows = promptLibraryState.rows()
      if (rows.isEmpty()) {
        draftController.restorePromptSnapshot(snapshot)
        showPromptLibraryMessage(AgentPromptBundle.message("popup.prompt.library.empty"))
        return@open
      }

      var chosenEntry: PromptLibraryEntry? = null
      val actionGroup = DefaultActionGroup().apply {
        rows.forEach { row ->
          add(PromptLibraryEntryAction(
            row = row,
            loadSavedPromptEntries = { promptLibraryState.savedPromptEntries },
            onChoose = { chosen ->
              chosenEntry = chosen
              draftController.replacePromptTextFromChooser(chosen.insertText)
              IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
              promptLibraryPopup?.cancel()
            },
            onSave = { recentEntry ->
              val savedPromptEntry = savePromptAsPersistentPrompt(recentEntry.insertText)
              if (savedPromptEntry != null) {
                promptLibraryState.markSaved(savedPromptEntry)
                showInfo(AgentPromptBundle.message("popup.prompt.library.saved"))
                refreshPromptLibraryPopup(row.normalizedPromptText)
              }
            },
            onRemove = { savedEntry ->
              removePersistentPrompt(savedEntry.insertText)
              promptLibraryState.markRemoved(savedEntry.insertText)
              showInfo(AgentPromptBundle.message("popup.prompt.library.removed"))
              refreshPromptLibraryPopup(row.normalizedPromptText)
            },
          ))
        }
      }
      val preselectCondition = preselectNormalizedPromptText?.let { normalizedPromptText ->
        Condition<AnAction> { action ->
          action is PromptLibraryEntryAction && action.normalizedPromptText == normalizedPromptText
        }
      }
      val popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(
          AgentPromptBundle.message("popup.prompt.library.title"),
          actionGroup,
          promptLibraryDataContext(),
          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
          false,
          null,
          rows.size.coerceAtMost(9),
          preselectCondition,
          null,
        )
      popup.addListSelectionListener { event ->
        if (event.valueIsAdjusting) {
          return@addListSelectionListener
        }
        val action = ((event.source as? JList<*>)?.selectedValue as? AnActionHolder)?.action as? PromptLibraryEntryAction
                     ?: return@addListSelectionListener
        val entry = action.currentEntry() ?: return@addListSelectionListener
        draftController.previewPromptText(entry.insertText)
      }
      popup.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          val skipPromptRestore = popupsWithoutPromptRestore.remove(popup)
          if (promptLibraryPopup === popup) {
            promptLibraryPopup = null
          }
          ApplicationManager.getApplication().invokeLater {
            if (!skipPromptRestore && chosenEntry == null && !project.isDisposed) {
              draftController.restorePromptSnapshot(snapshot)
              IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
            }
          }
        }
      })
      val previewEntry = rows
                           .firstOrNull { row -> row.normalizedPromptText == preselectNormalizedPromptText }
                           ?.let(promptLibraryState::resolveEntry)
                         ?: rows.firstNotNullOfOrNull(promptLibraryState::resolveEntry)
      previewEntry?.let { entry -> draftController.previewPromptText(entry.insertText) }
      promptLibraryPopup = popup
      popup.showUnderneathOf(view.promptLibraryIconLabel)
    }

    openPromptLibraryPopup(null)
  }

  private fun promptLibraryDataContext(): DataContext {
    return invocationData.dataContextOrNull() ?: DataContext.EMPTY_CONTEXT
  }

  private fun savePromptAsPersistentPrompt(promptText: String): AgentPromptSavedPromptEntry? {
    val savedPromptEntry = uiStateService.savePersistentPrompt(promptText)
    if (savedPromptEntry == null) {
      showError(AgentPromptBundle.message("popup.prompt.library.save.error"))
    }
    return savedPromptEntry
  }

  private fun removePersistentPrompt(promptText: String) {
    uiStateService.removePersistentPrompt(promptText)
  }

  private fun showPromptLibraryMessage(message: @Nls String) {
    JBPopupFactory.getInstance()
      .createMessage(message)
      .showUnderneathOf(view.promptLibraryIconLabel)
  }

  private fun reusableSourceProjectPaths(): List<String> {
    val sourceProjectBasePath = launcherProvider()
      ?.resolveSourceProject(invocationData)
      ?.basePath
    return resolveClaudeSlashCompletionProjectPaths(
      workingProjectPath = submitController.resolveWorkingProjectPath(),
      sourceProjectBasePath = sourceProjectBasePath,
      projectBasePath = project.basePath,
    )
  }

  private fun onPromptChanged() {
    draftController.onPromptChanged()
    updateSendAvailability()
    clearStatus()
  }

  private fun autoPopupCommandCompletionIfNeeded(event: DocumentEvent) {
    val editor = promptArea.editor ?: return
    if (!editor.contentComponent.hasFocus()) {
      return
    }
    if (LookupManager.getActiveLookup(editor) != null) {
      return
    }

    val selectedProvider = providerSelector.selectedProvider?.bridge?.provider
    val documentText = event.document.immutableCharSequence.toString()
    val sourceProjectBasePath = launcherProvider()
      ?.resolveSourceProject(invocationData)
      ?.basePath
    if (shouldAutoPopupClaudeSlashCompletion(
        selectedProvider = selectedProvider,
        workingProjectPaths = resolveClaudeSlashCompletionProjectPaths(
          workingProjectPath = submitController.resolveWorkingProjectPath(),
          sourceProjectBasePath = sourceProjectBasePath,
          projectBasePath = project.basePath,
        ),
        text = documentText,
        offsetAfterChange = event.offset + event.newLength,
        insertedFragment = event.newFragment,
      )
    ) {
      invokePromptCompletionWhenReady(editor, expectedPrefix = '/')
      return
    }

    if (!shouldAutoPopupCodexSkillCompletion(
        selectedProvider = selectedProvider,
        text = documentText,
        offsetAfterChange = event.offset + event.newLength,
        insertedFragment = event.newFragment,
      )
    ) {
      return
    }

    popupScope.launch {
      val skillEntries = loadCodexSkillCompletionEntries()
      if (skillEntries.isEmpty()) {
        return@launch
      }
      codexSkillCompletionEntries = skillEntries
      withContext(Dispatchers.EDT) {
        invokePromptCompletionWhenReady(editor, expectedPrefix = CODEX_SKILL_PREFIX)
      }
    }
  }

  private suspend fun loadCodexSkillCompletionEntries(): List<AgentPromptReusableSourceEntry> {
    val launcher = launcherProvider() ?: return emptyList()
    val projectPath = submitController.resolveWorkingProjectPath()?.takeIf(String::isNotBlank) ?: return emptyList()
    return runCatching { launcher.listReusablePromptSourceEntries(projectPath, AgentSessionProvider.CODEX) }
      .getOrDefault(emptyList())
      .filter { entry ->
        entry.kind == AgentPromptReusableSourceKind.SKILL && entry.insertText.trim().startsWith('$')
      }
  }

  private fun invokePromptCompletionWhenReady(editor: Editor, expectedPrefix: Char) {
    popupScope.launch {
      withContext(Dispatchers.EDT) {
        if (project.isDisposed || editor.isDisposed || !editor.contentComponent.hasFocus()) {
          return@withContext
        }
        if (LookupManager.getActiveLookup(editor) != null) {
          return@withContext
        }
        val text = editor.document.immutableCharSequence.toString()
        val caretOffset = editor.caretModel.offset
        val currentPrefix = when (expectedPrefix) {
          '/' -> findClaudeSlashCompletionPrefix(text, caretOffset)
          CODEX_SKILL_PREFIX -> findCodexSkillCompletionPrefix(text, caretOffset)
          else -> null
        }
        if (currentPrefix != expectedPrefix.toString()) {
          return@withContext
        }

        CodeCompletionHandlerBase(CompletionType.BASIC, false, true, true).invokeCompletion(project, editor, 1)
      }
    }
  }

  private fun refreshProviders() {
    providerSelector.refresh()
    updateProviderOptionsVisibility()
  }

  private fun applyInitialAddContextTarget(target: AgentPromptAddContextTargetCandidate?) {
    if (target == null) {
      existingTaskController.clearSelection()
      setTargetMode(PromptTargetMode.NEW_TASK)
      return
    }

    launchState.selectedWorkingProjectPath = target.projectPath
    providerSelector.selectProvider(target.provider, target.launchMode)
    existingTaskController.selectedExistingTaskId = target.threadId
    setTargetMode(PromptTargetMode.EXISTING_TASK)
    reloadExistingTasks()
  }

  private fun updateProviderOptionsVisibility() {
    val extensionTab = contextState.activeExtensionTab
    val isStandardTab = extensionTab == null
    val isNewTaskLaunch = isStandardTab && currentTargetMode() == PromptTargetMode.NEW_TASK
    providerSelector.setProviderOptionsVisible(isStandardTab)
    generationSettingsController.setControlsVisibility(
      providerSelectorVisible = isNewTaskLaunch || extensionTab?.extension?.showsProviderSelector() == true,
      generationControlsVisible = isNewTaskLaunch || extensionTab?.extension?.showsGenerationControls() == true,
    )
    syncContainerModeState()
  }

  private fun syncContainerModeState(requestedSelection: Boolean = view.containerModeAction.selected) {
    val state = resolveContainerModeOptionState(
      selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
      isExtensionTab = contextState.activeExtensionTab != null,
      requestedSelection = requestedSelection,
      supportsContainerMode = ::isContainerModeSupported,
      isContainerRuntimeAvailable = ::isContainerModeRuntimeAvailable,
    )
    view.headerControls.setContainerModeState(
      visible = state.visible,
      enabled = state.enabled,
      selected = state.selected,
      tooltipText = if (state.showUnavailableTooltip) {
        AgentPromptBundle.message("popup.option.container.mode.unavailable.tooltip")
      }
      else {
        null
      },
    )
    view.rightHeaderPanel.revalidate()
    view.rightHeaderPanel.repaint()
  }

  private fun isContainerModeSelectedForCurrentState(): Boolean {
    return resolveContainerModeOptionState(
      selectedProvider = providerSelector.selectedProvider?.bridge?.provider,
      isExtensionTab = contextState.activeExtensionTab != null,
      requestedSelection = view.containerModeAction.selected,
      supportsContainerMode = ::isContainerModeSupported,
      isContainerRuntimeAvailable = ::isContainerModeRuntimeAvailable,
    ).selected
  }

  private fun isContainerModeSupported(provider: AgentSessionProvider): Boolean {
    return AgentPromptContainerLauncher.findInstance()?.supportsProvider(provider) == true
  }

  private fun isContainerModeRuntimeAvailable(provider: AgentSessionProvider): Boolean {
    return AgentPromptContainerLauncher.findInstance()?.let { launcher ->
      launcher.supportsProvider(provider) && launcher.isAvailable()
    } == true
  }

  private fun reloadExistingTasks() {
    val launcher = launcherProvider()
    existingTaskController.reload(
      selectedProviderEntry = providerSelector.selectedProvider,
      launcher = launcher,
      projectPath = submitController.resolveWorkingProjectPath(),
      isPopupActive = isPopupActive,
    )
  }

  private fun updateTargetModeUi() {
    val isExtensionTab = contextState.activeExtensionTab != null
    val mode = currentTargetMode()
    view.existingTaskScrollPane.isVisible = !isExtensionTab && mode == PromptTargetMode.EXISTING_TASK
    view.rootPanel.revalidate()
    movePopupToFitScreen()
    if (!isExtensionTab && mode == PromptTargetMode.EXISTING_TASK) {
      if (!existingTaskController.hasLoadedEntries()) {
        reloadExistingTasks()
      }
      refreshPreselection()
    }
    updateProviderOptionsVisibility()
    refreshSuggestions()
    refreshFooterHintForCurrentState()
  }

  private fun refreshPreselection() {
    popupScope.launch {
      val preferredId = resolvePreferredThreadId() ?: return@launch
      existingTaskController.setPreselection(preferredId)
    }
  }

  private suspend fun resolvePreferredThreadId(): String? {
    val launcher = launcherProvider() ?: return null
    val projectPath = submitController.resolveWorkingProjectPath() ?: return null
    val provider = providerSelector.selectedProvider?.bridge?.provider ?: return null
    return runCatching { launcher.listAddContextTargetCandidates(projectPath) }
      .getOrDefault(emptyList())
      .firstOrNull { candidate -> candidate.selected && candidate.provider == provider }
      ?.threadId
  }

  private fun updateSendAvailability() {
    submitController.updateSendAvailability()
  }

  private fun refreshSuggestions() {
    if (contextState.activeExtensionTab != null) {
      suggestionController.clearSuggestions()
      return
    }

    suggestionController.reloadSuggestions(
      AgentPromptSuggestionRequest(
        project = project,
        projectPath = submitController.resolveWorkingProjectPath(),
        targetModeId = currentTargetMode().name,
        contextItems = contextController.buildVisibleContextEntries().map(ContextEntry::item),
      )
    )
  }

  private fun resolveTaskKey(panel: JPanel?): String? {
    if (panel == null) return null
    val mode = panel.getClientProperty("targetMode") as? PromptTargetMode
    if (mode != null) return mode.name
    return contextState.activeExtensionTabs.firstOrNull { it.tabPanel === panel }?.let(::resolveExtensionTaskKey)
  }

  private fun currentContextItems(): List<AgentPromptContextItem> {
    return contextController.buildVisibleContextEntries().map(ContextEntry::item)
  }

  private fun resolveExtensionTaskKey(
    entry: AgentPromptPaletteExtensionTab,
    contextItems: List<AgentPromptContextItem> = currentContextItems(),
  ): String {
    return AgentPromptPaletteExtensionContext.withContextItems(project, contextItems) {
      AgentPromptExtensionDraftDecisions.taskKey(entry.taskKeyPrefix, entry.extension.getInitialPrompt(project)?.kind)
    }
  }

  private fun refreshExtensionTaskDraftsFromContext() {
    val contextItems = currentContextItems()
    for (entry in contextState.activeExtensionTabs) {
      val taskKey = resolveExtensionTaskKey(entry, contextItems)
      val updatedState = AgentPromptPaletteExtensionContext.withContextItems(project, contextItems) {
        val initialPrompt = entry.extension.getInitialPrompt(project)
        val currentState = draftState.taskPromptStates[taskKey]
        if (currentState == null) {
          initialPrompt
            ?.let { restoredTaskPromptDraftState(it.content) }
        }
        else {
          synchronizeExtensionDraftState(currentState, initialPrompt?.kind) { prompt ->
            entry.extension.synchronizePrompt(project, prompt)
          }
        }
      }

      if (updatedState == null) {
        draftState.taskPromptStates.remove(taskKey)
      }
      else {
        draftState.taskPromptStates[taskKey] = updatedState
      }

      if (contextState.activeExtensionTab === entry) {
        draftState.activeTaskKey = taskKey
      }
    }

    if (contextState.activeExtensionTab != null) {
      val taskKey = draftState.activeTaskKey ?: return
      val updatedText = draftState.taskPromptStates[taskKey]?.liveText.orEmpty()
      if (promptArea.text != updatedText) {
        draftController.setPromptAreaTextProgrammatically(updatedText)
      }
    }
  }

  private fun currentTargetMode(): PromptTargetMode {
    val selectedComponent = view.tabbedPane.selectedComponent as? JPanel ?: return PromptTargetMode.NEW_TASK
    return selectedComponent.getClientProperty("targetMode") as? PromptTargetMode ?: PromptTargetMode.NEW_TASK
  }

  private fun setTargetMode(mode: PromptTargetMode) {
    val index = findTabIndexForMode(mode) ?: return
    view.tabbedPane.selectedIndex = index
  }

  private fun findTabIndexForMode(mode: PromptTargetMode): Int? {
    for (i in 0 until view.tabbedPane.tabCount) {
      val component = view.tabbedPane.getComponentAt(i) as? JPanel ?: continue
      if (component.getClientProperty("targetMode") == mode) {
        return i
      }
    }
    return null
  }

  private fun clearStatus() {
    val extensionTab = contextState.activeExtensionTab
    val message = if (extensionTab != null) {
      extensionTab.extension.getFooterHint() ?: AgentPromptBundle.message("popup.footer.hint.default.tab")
    }
    else {
      AgentPromptBundle.message(
        resolveDefaultFooterHintMessageKey(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge,
          hasNextPromptTab = view.tabbedPane.selectedIndex in 0 until view.tabbedPane.tabCount - 1,
        )
      )
    }
    view.statusStrip.showInfo(message)
  }

  private fun refreshFooterHintForCurrentState() {
    if (shouldShowExistingTaskSelectionHint(
        targetMode = currentTargetMode(),
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        selectedProvider = providerSelector.selectedProvider?.bridge,
      )
    ) {
      showInfo(AgentPromptBundle.message("popup.status.existing.select.task"))
      return
    }

    clearStatus()
  }

  private fun handleContextChanged(message: @Nls String) {
    refreshExtensionTaskDraftsFromContext()
    updateTargetModeUi()
    updateSendAvailability()
    showInfo(message)
  }

  private fun handleWorkingProjectPathSelected() {
    contextController.refreshContextEntries()
    contextController.resolveExtensionTabs()
    refreshExtensionTaskDraftsFromContext()
    updateTargetModeUi()
    if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
      existingTaskController.clearSelection()
      reloadExistingTasks()
    }
    updateSendAvailability()
  }

  private fun closeAfterSuccessfulSubmit() {
    closePopup()
  }

  private fun runManageProfilesDialog(openDialog: AgentPromptLaunchProfileEditorOpenDialog) {
    closePopup()
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      openDialog {
        if (!project.isDisposed) {
          project.service<AgentPromptPalettePopupService>().show(invocationData)
        }
      }
    }
  }

  private fun showError(message: @Nls String) {
    view.statusStrip.showError(message)
  }

  private fun showInfo(message: @Nls String) {
    view.statusStrip.showInfo(message)
  }
}

internal class PromptLibraryEntryAction(
  private val row: PromptLibraryRow,
  private val loadSavedPromptEntries: () -> List<AgentPromptSavedPromptEntry>,
  private val onChoose: (PromptLibraryEntry) -> Unit,
  private val onSave: (PromptLibraryEntry.RecentPrompt) -> Unit,
  private val onRemove: (PromptLibraryEntry.SavedPrompt) -> Unit,
) : DumbAwareAction() {
  val normalizedPromptText: String
    get() = row.normalizedPromptText

  fun currentEntry(): PromptLibraryEntry? = row.resolveEntry(loadSavedPromptEntries())

  override fun update(e: AnActionEvent) {
    val entry = currentEntry()
    if (entry == null) {
      e.presentation.isEnabledAndVisible = false
      e.presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, null)
      e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, null)
      e.presentation.putClientProperty(ActionUtil.INLINE_ACTIONS, null)
      return
    }
    e.presentation.isEnabledAndVisible = true
    e.presentation.text = entry.displayText()
    e.presentation.description = entry.searchText
    e.presentation.icon = null
    e.presentation.putClientProperty(ActionUtil.SECONDARY_TEXT, entry.secondaryText())
    e.presentation.putClientProperty(ActionUtil.SECONDARY_ICON, null)
    e.presentation.putClientProperty(ActionUtil.SEARCH_TAG, entry.searchText)
    val inlineAction = when (entry) {
      is PromptLibraryEntry.SavedPrompt -> RemoveSavedPromptAction(entry, onRemove)
      is PromptLibraryEntry.PromptFile -> null
      is PromptLibraryEntry.RecentPrompt -> SaveRecentPromptAction(entry, onSave)
    }
    e.presentation.putClientProperty(ActionUtil.INLINE_ACTIONS, inlineAction?.let { listOf(it) })
  }

  override fun actionPerformed(e: AnActionEvent) {
    currentEntry()?.let(onChoose)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class SaveRecentPromptAction(
  private val entry: PromptLibraryEntry.RecentPrompt,
  private val onSave: (PromptLibraryEntry.RecentPrompt) -> Unit,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.text = AgentPromptBundle.message("popup.prompt.library.save")
    e.presentation.description = AgentPromptBundle.message("popup.prompt.library.save")
    e.presentation.icon = AllIcons.Actions.MenuSaveall
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Always
  }

  override fun actionPerformed(e: AnActionEvent) {
    onSave(entry)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class RemoveSavedPromptAction(
  private val entry: PromptLibraryEntry.SavedPrompt,
  private val onRemove: (PromptLibraryEntry.SavedPrompt) -> Unit,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.text = AgentPromptBundle.message("popup.prompt.library.remove")
    e.presentation.description = AgentPromptBundle.message("popup.prompt.library.remove")
    e.presentation.icon = AllIcons.General.Remove
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Always
  }

  override fun actionPerformed(e: AnActionEvent) {
    onRemove(entry)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
