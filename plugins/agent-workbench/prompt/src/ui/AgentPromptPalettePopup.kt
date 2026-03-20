// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.dynatrace.hash4j.hashing.HashValue128
import com.intellij.CommonBundle
import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY
import com.intellij.agent.workbench.prompt.context.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.context.AgentPromptImagePasteHandler
import com.intellij.agent.workbench.prompt.context.IMAGE_PASTE_SOURCE_ID
import com.intellij.agent.workbench.prompt.context.dataContextOrNull
import com.intellij.agent.workbench.prompt.ui.AgentPromptContextRemovalDecisions.removeManualContextItemsAfterExplicitRemoval
import com.intellij.agent.workbench.prompt.ui.AgentPromptContextRemovalDecisions.resolveContextEntriesAfterRemoval
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchers
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSelectionMode
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSourceBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSources
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPaletteExtension
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPaletteExtensionContext
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPaletteExtensions
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.LanguageTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.ChangeListener

private const val CONTEXT_SOFT_CAP_CHARS = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS

internal data class ManualContextAvailability(
  @JvmField val sourceProject: Project,
  @JvmField val sources: List<AgentPromptManualContextSourceBridge>,
)

internal fun resolveManualContextAvailability(
  hostProject: Project,
  invocationData: AgentPromptInvocationData,
  launcher: AgentPromptLauncherBridge?,
  sources: List<AgentPromptManualContextSourceBridge>,
): ManualContextAvailability? {
  val sourceProject = when {
    launcher == null -> hostProject
    else -> launcher.resolveSourceProject(invocationData) ?: return null
  }

  return ManualContextAvailability(
    sourceProject = sourceProject,
    sources = sources
      .filter { source -> source.isAvailable(sourceProject) }
      .sortedWith(compareBy(AgentPromptManualContextSourceBridge::order, AgentPromptManualContextSourceBridge::sourceId)),
  )
}

private class AgentPromptTextField(project: Project) : LanguageTextField(
  MarkdownFileType.INSTANCE.language, project, "", false,
) {
  init {
    setPlaceholder(AgentPromptBundle.message("popup.prompt.placeholder"))
    setShowPlaceholderWhenFocused(true)
    addSettingsProvider { editor ->
      editor.settings.isUseSoftWraps = true
      editor.settings.isPaintSoftWraps = false
      editor.settings.isLineNumbersShown = false
      editor.settings.setGutterIconsShown(false)
      editor.settings.isFoldingOutlineShown = false
      editor.settings.isAdditionalPageAtBottom = false
      editor.settings.isRightMarginShown = false
      editor.setVerticalScrollbarVisible(true)
      editor.setHorizontalScrollbarVisible(false)
    }
  }

  override fun createEditor(): EditorEx {
    val ed = super.createEditor()
    ed.highlighter = EditorHighlighterFactory.getInstance()
      .createEditorHighlighter(project, MarkdownFileType.INSTANCE)
    ed.backgroundColor = JBUI.CurrentTheme.Popup.BACKGROUND
    ed.gutterComponentEx.background = JBUI.CurrentTheme.Popup.BACKGROUND
    return ed
  }
}

internal class AgentPromptPalettePopup(
  private val invocationData: AgentPromptInvocationData,
  private val providersProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val launcherProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
  private val onClosed: (() -> Unit)? = null,
) : AgentPromptPalettePopupSession {
  private val project: Project = invocationData.project
  private val contextResolverService: AgentPromptContextResolverService = project.service()
  private val uiStateService: AgentPromptUiSessionStateService = project.service()
  private val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPalettePopup::class.java.classLoader)

  private val promptArea = AgentPromptTextField(project)
  private val suggestions = AgentPromptSuggestionsComponent(::applySuggestedPrompt)
  private val contextChips = AgentPromptContextChipsComponent(::removeContextEntry)

  private lateinit var tabbedPane: JBTabbedPane
  private lateinit var providerIconLabel: JBLabel
  private lateinit var existingTaskScrollPane: JBScrollPane
  private lateinit var providerOptionsPanel: JPanel
  private lateinit var footerLabel: JBLabel
  private lateinit var providerSelector: AgentPromptProviderSelector
  private lateinit var existingTaskController: AgentPromptExistingTaskController

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private var clearDraftOnClose: Boolean = false
  private var canSubmitNow: Boolean = false
  private var autoContextEntries: List<ContextEntry> = emptyList()
  private var contextEntries: List<ContextEntry> = emptyList()
  private var initialAutoContextFingerprint: HashValue128? = null
  private val removedAutoLogicalItemIds = LinkedHashSet<String>()
  private val manualContextItemsBySourceId: MutableMap<String, List<AgentPromptContextItem>> = LinkedHashMap()
  private var selectedLaunchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD
  private var selectedWorkingProjectPath: String? = null
  private var existingTaskSearchQuery: String = ""
  private var activeExtensionTabs: List<ExtensionTabEntry> = emptyList()
  private var activeExtensionTab: ExtensionTabEntry? = null
  private var activeTaskKey: String? = null
  private val taskPromptStates: MutableMap<String, AgentPromptTaskDraftState> = HashMap()
  private var promptTextUpdateOrigin: PromptTextUpdateOrigin? = null

  @Suppress("RAW_SCOPE_CREATION")
  private val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)
  private val suggestionController by lazy(LazyThreadSafetyMode.NONE) {
    AgentPromptSuggestionController(
      popupScope = popupScope,
      onSuggestionsUpdated = suggestions::render,
    )
  }

  private class ExtensionTabEntry(
    @JvmField val extension: AgentPromptPaletteExtension,
    @JvmField val tabPanel: JPanel,
    @JvmField val taskKeyPrefix: String,
  )

  private enum class PromptTextUpdateOrigin {
    PROGRAMMATIC,
  }

  private fun resolveTaskKey(panel: JPanel?): String? {
    if (panel == null) return null
    val mode = panel.getClientProperty("targetMode") as? PromptTargetMode
    if (mode != null) return mode.name
    return activeExtensionTabs.firstOrNull { it.tabPanel === panel }?.let(::resolveExtensionTaskKey)
  }

  private fun currentContextItems(): List<AgentPromptContextItem> {
    return contextEntries.map(ContextEntry::item)
  }

  private fun resolveExtensionTaskKey(
    entry: ExtensionTabEntry,
    contextItems: List<AgentPromptContextItem> = currentContextItems(),
  ): String {
    return AgentPromptPaletteExtensionContext.withContextItems(project, contextItems) {
      AgentPromptExtensionDraftDecisions.taskKey(entry.taskKeyPrefix, entry.extension.getInitialPrompt(project)?.kind)
    }
  }

  private fun matchesExtensionTaskKey(entry: ExtensionTabEntry, taskKey: String): Boolean {
    return AgentPromptExtensionDraftDecisions.matchesTaskKey(entry.taskKeyPrefix, taskKey)
  }

  override fun show() {
    val content = createContentPanel()
    refreshProviders()
    loadInitialContext()
    resolveExtensionTabs()

    val draft = restoreDraft()
    activeTaskKey = resolveTaskKey(tabbedPane.selectedComponent as? JPanel)
    restoreTaskDrafts(draft)
    val preferExtensions = invocationData.attributes[AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY] == true
    if (preferExtensions) {
      selectAutoSelectExtensionTab()
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
        suggestionController.dispose()
        popupScope.cancel("Agent prompt popup closed")
        saveProviderPreferences()
        if (clearDraftOnClose) {
          uiStateService.clearDraft()
        }
        else {
          saveDraft()
        }
        onClosed?.invoke()
      }
    })

    attachHandlers()
    createdPopup.showCenteredInCurrentWindow(project)
  }

  override fun requestFocus() {
    val currentPopup = popup ?: return
    if (!currentPopup.isVisible) {
      return
    }

    currentPopup.setRequestFocus(true)
    val focusComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(currentPopup.content) ?: promptArea
    IdeFocusManager.getInstance(project).requestFocusInProject(focusComponent, project)
  }

  override fun isVisible(): Boolean {
    return popup?.isVisible == true
  }

  private fun createContentPanel(): JPanel {
    val promptProviderOptionsPanel = createProviderOptionsPanel()
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestions.component,
      contextChipsPanel = contextChips.component,
      providerOptionsPanel = promptProviderOptionsPanel,
      onProviderIconClicked = ::showProviderChooser,
      onExistingTaskSelected = ::onExistingTaskSelected,
    )
    val availability = resolveManualContextAvailability()
    if (availability != null && availability.sources.isNotEmpty()) {
      view.addContextButton.addActionListener {
        showManualContextSourceChooser(anchorComponent = view.addContextButton)
      }
    }
    else {
      view.addContextButton.isVisible = false
    }
    tabbedPane = view.tabbedPane
    providerIconLabel = view.providerIconLabel
    existingTaskScrollPane = view.existingTaskScrollPane
    providerOptionsPanel = checkNotNull(view.providerOptionsPanel)
    footerLabel = view.footerLabel
    providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      providerIconLabel = providerIconLabel,
      providerOptionsPanel = providerOptionsPanel,
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

  private fun createProviderOptionsPanel(): JPanel {
    return JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      isVisible = false
    }
  }

  private fun resolveManualContextAvailability(launcher: AgentPromptLauncherBridge? = launcherProvider()): ManualContextAvailability? {
    return resolveManualContextAvailability(
      hostProject = project,
      invocationData = invocationData,
      launcher = launcher,
      sources = AgentPromptManualContextSources.allSources(),
    )
  }

  private fun showManualContextSourceChooser(anchorComponent: JComponent) {
    val launcher = launcherProvider()
    val availability = resolveManualContextAvailability(launcher) ?: return
    val sourceProject = availability.sourceProject
    val sources = availability.sources
    if (sources.isEmpty()) {
      return
    }

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(sources)
      .setRenderer(object : ColoredListCellRenderer<AgentPromptManualContextSourceBridge>() {
        override fun customizeCellRenderer(
          list: JList<out AgentPromptManualContextSourceBridge>,
          value: AgentPromptManualContextSourceBridge?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          value ?: return
          append(value.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
      })
      .setItemChosenCallback { source ->
        source.showPicker(
          AgentPromptManualContextPickerRequest(
            hostProject = project,
            sourceProject = sourceProject,
            invocationData = invocationData,
            workingProjectPath = resolveWorkingProjectPath(launcher),
            currentItems = manualContextItemsBySourceId[source.sourceId].orEmpty(),
            anchorComponent = anchorComponent,
            onSelected = { item -> applyManualContextSelection(source = source, item = item) },
            onError = ::showError,
          )
        )
      }
      .createPopup()
      .showUnderneathOf(anchorComponent)
  }

  private fun applyManualContextSelection(
    source: AgentPromptManualContextSourceBridge,
    item: AgentPromptContextItem,
  ) {
    val updatedItems = when (source.selectionMode) {
      AgentPromptManualContextSelectionMode.REPLACE -> listOf(item)
      AgentPromptManualContextSelectionMode.APPEND -> manualContextItemsBySourceId[source.sourceId].orEmpty() + item
    }
    manualContextItemsBySourceId[source.sourceId] = updatedItems
    refreshContextEntries()
    resolveExtensionTabs()
    updateTargetModeUi()
    updateSendAvailability()
    showInfo(AgentPromptBundle.message("popup.status.context.added"))
  }

  private fun installImagePasteHandler() {
    promptArea.addSettingsProvider { editor ->
      editor.putUserData(AGENT_PROMPT_IMAGE_PASTE_HANDLER_KEY, AgentPromptImagePasteHandler { item ->
        val existing = manualContextItemsBySourceId[IMAGE_PASTE_SOURCE_ID].orEmpty()
        manualContextItemsBySourceId[IMAGE_PASTE_SOURCE_ID] = existing + item
        refreshContextEntries()
        resolveExtensionTabs()
        updateTargetModeUi()
        updateSendAvailability()
        showInfo(AgentPromptBundle.message("popup.status.context.added"))
      })
    }
  }

  private fun onExistingTaskSelected(selected: ThreadEntry) {
    existingTaskController.onUserSelected(selected)
    updateSendAvailability()
    refreshFooterHintForCurrentState()
  }

  private fun attachHandlers() {
    installImagePasteHandler()

    installPromptEnterHandlers(
      promptArea = promptArea,
      canSubmit = { canSubmitNow },
      isTabQueueEnabled = {
        isTabQueueShortcutEnabled(
          targetMode = currentTargetMode(),
          selectedProvider = providerSelector.selectedProvider?.bridge,
          hasNextPromptTab = tabbedPane.selectedIndex in 0 until tabbedPane.tabCount - 1,
        )
      },
      onSubmit = ::submit,
      onTabFocusTransfer = { selectAdjacentPromptTab(tabbedPane, 1) },
      onTabBackwardFocusTransfer = { selectAdjacentPromptTab(tabbedPane, -1) },
    )

    tabbedPane.addChangeListener(ChangeListener {
      handleTabSwitch()
      updateTargetModeUi()
      updateSendAvailability()
      popup?.moveToFitScreen()
    })

    promptArea.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
      override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
        onPromptChanged()
      }
    })
  }

  private fun onPromptChanged() {
    if (promptTextUpdateOrigin == null) {
      updateActiveTaskPromptState { state ->
        applyUserEditToDraftState(state, promptArea.text)
      }
    }
    else {
      syncLivePromptTextForSelectedTab(promptArea.text)
    }
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
    updateProviderOptionsVisibility()
    refreshSuggestions()
    refreshFooterHintForCurrentState()
  }

  private fun refreshSuggestions() {
    if (activeExtensionTab != null) {
      suggestionController.clearSuggestions()
      return
    }

    val launcher = launcherProvider()
    suggestionController.reloadSuggestions(
      AgentPromptSuggestionRequest(
        project = project,
        projectPath = resolveWorkingProjectPath(launcher),
        targetModeId = currentTargetMode().name,
        contextItems = buildVisibleContextEntries(launcher).map(ContextEntry::item),
      )
    )
  }

  private fun applySuggestedPrompt(candidate: AgentPromptSuggestionCandidate) {
    updateActiveTaskPromptState { state ->
      applySuggestedPromptToDraftState(state, candidate.promptText)
    }
    setPromptAreaText(candidate.promptText)
    IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
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
    updateProviderOptionsVisibility()
  }

  private fun updateProviderOptionsVisibility() {
    providerOptionsPanel.isVisible = activeExtensionTab == null && providerOptionsPanel.componentCount > 0
    providerOptionsPanel.revalidate()
    providerOptionsPanel.repaint()
  }

  private fun showProviderChooser() {
    providerSelector.showChooser(onUnavailable = ::showError) {
      selectedLaunchMode = providerSelector.selectedLaunchMode
      if (currentTargetMode() == PromptTargetMode.EXISTING_TASK) {
        existingTaskController.clearSelection()
        reloadExistingTasks()
      }
      updateProviderOptionsVisibility()
      updateSendAvailability()
      refreshFooterHintForCurrentState()
    }
  }

  private fun loadInitialContext() {
    val resolved = contextResolverService.collectDefaultContext(invocationData)
    initialAutoContextFingerprint = computeContextFingerprint(resolved)
    removedAutoLogicalItemIds.clear()
    autoContextEntries = resolved.map { item ->
      ContextEntry(
        item = item,
        origin = ContextEntryOrigin.AUTO,
      )
    }
    manualContextItemsBySourceId.clear()
    refreshContextEntries()
  }

  private fun refreshContextEntries(launcher: AgentPromptLauncherBridge? = launcherProvider()) {
    contextEntries = buildVisibleContextEntries(launcher)
    contextChips.render(contextEntries)
  }

  private fun buildVisibleContextEntries(launcher: AgentPromptLauncherBridge? = launcherProvider()): List<ContextEntry> {
    return materializeVisibleContextEntries(
      autoEntries = autoContextEntries,
      manualItemsBySourceId = manualContextItemsBySourceId,
      projectPath = resolveContextProjectBasePath(launcher),
    )
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
    activeExtensionTabs = activeExtensionTabs + ExtensionTabEntry(extension = extension, tabPanel = panel, taskKeyPrefix = key)
  }

  private fun removeExtensionTab(entry: ExtensionTabEntry) {
    val index = (0 until tabbedPane.tabCount).firstOrNull { tabbedPane.getComponentAt(it) === entry.tabPanel }
    if (index != null) {
      tabbedPane.removeTabAt(index)
    }
    activeExtensionTabs = activeExtensionTabs.filter { it !== entry }
    taskPromptStates.remove(entry.taskKey)
    if (activeExtensionTab === entry) {
      activeExtensionTab = null
      setTargetMode(PromptTargetMode.NEW_TASK)
    }
  }

  private fun selectAutoSelectExtensionTab() {
    val items = contextEntries.map { it.item }
    val target = activeExtensionTabs.firstOrNull { it.extension.shouldAutoSelect(items) } ?: return
    val index = (0 until tabbedPane.tabCount).firstOrNull { tabbedPane.getComponentAt(it) === target.tabPanel }
    if (index != null) {
      tabbedPane.selectedIndex = index
    }
  }

  private fun restoreTaskDrafts(draft: AgentPromptUiDraft) {
    val savedDrafts = draft.taskDrafts

    // Built-in tabs: restore from taskDrafts, fall back to legacy promptText for NEW_TASK
    val newTaskKey = PromptTargetMode.NEW_TASK.name
    taskPromptStates[newTaskKey] = restoredTaskPromptDraftState(savedDrafts[newTaskKey] ?: draft.promptText)
    val existingTaskKey = PromptTargetMode.EXISTING_TASK.name
    savedDrafts[existingTaskKey]?.let { taskPromptStates[existingTaskKey] = restoredTaskPromptDraftState(it) }

    // Extension tabs: restore from taskDrafts, fall back to extension's initial text
    for (entry in activeExtensionTabs) {
      val savedText = savedDrafts[entry.taskKey]
      if (!savedText.isNullOrBlank()) {
        taskPromptStates[entry.taskKey] = restoredTaskPromptDraftState(savedText)
      }
      else {
        val initialText = entry.extension.getInitialPromptText(project)
        if (!initialText.isNullOrBlank()) {
          taskPromptStates[entry.taskKey] = restoredTaskPromptDraftState(initialText)
        }
      }
    }
  }

  private fun handleTabSwitch() {
    savePromptTextForSelectedTab()
    loadPromptTextForSelectedTab()
  }

  private fun savePromptTextForSelectedTab() {
    syncLivePromptTextForSelectedTab(promptArea.text)
  }

  private fun loadPromptTextForSelectedTab() {
    val newPanel = tabbedPane.selectedComponent as? JPanel
    val newKey = resolveTaskKey(newPanel)
    activeTaskKey = newKey
    activeExtensionTab = newPanel?.let { comp -> activeExtensionTabs.firstOrNull { it.tabPanel === comp } }
    setPromptAreaText(newKey?.let { taskPromptStates[it]?.liveText } ?: "")
  }

  private fun removeContextEntry(entry: ContextEntry) {
    if (entry.origin == ContextEntryOrigin.MANUAL) {
      val updatedManualItems = removeManualContextItemsAfterExplicitRemoval(
        manualItemsBySourceId = manualContextItemsBySourceId,
        removedEntry = entry,
        projectPath = resolveContextProjectBasePath(launcherProvider()),
      )
      if (updatedManualItems == manualContextItemsBySourceId) {
        return
      }
      manualContextItemsBySourceId.clear()
      manualContextItemsBySourceId.putAll(updatedManualItems)
    }
    else {
      val beforeEntries = autoContextEntries
      val updatedEntries = resolveContextEntriesAfterRemoval(beforeEntries, entry.id)
      removedAutoLogicalItemIds.addAll(
        collectRemovedLogicalItemIds(
          beforeEntries = beforeEntries,
          afterEntries = updatedEntries,
        )
      )
      autoContextEntries = updatedEntries
    }
    refreshContextEntries()
    resolveExtensionTabs()
    refreshExtensionTaskDraftsFromContext()
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
    val hasPrompt = promptArea.document.immutableCharSequence.isNotBlank()
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
          val baseDataContext = invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(promptArea)
          val dataContext = buildExtensionActionDataContext(
            baseDataContext = baseDataContext,
            selectedProviderId = providerSelector.selectedProvider?.bridge?.provider?.value,
          )
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
      if (shouldRetrySubmitAfterWorkingProjectPathSelection(
          validationErrorKey = validationErrorKey,
          requestWorkingProjectPathSelection = launcher?.let { promptLauncher ->
            { promptWorkingProjectPathSelection(promptLauncher) { submit() } }
          },
        )) {
        return
      }
      val message = if (validationErrorKey == "popup.error.provider.unavailable") {
        AgentPromptBundle.message(validationErrorKey, selectedProviderEntry?.displayName ?: "")
      }
      else {
        AgentPromptBundle.message(validationErrorKey)
      }
      reportPromptSubmitBlocked(
        validationErrorKey = validationErrorKey,
        provider = selectedProviderEntry?.bridge?.provider,
        launchMode = selectedLaunchMode,
      )
      showError(message)
      return
    }

    val prompt = promptArea.text.trim()
    val providerEntry = selectedProviderEntry ?: return
    val effectiveProjectPath = projectPath ?: return

    val selectedContextItems = buildVisibleContextEntries(launcher).map(ContextEntry::item)
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
    val effectiveProviderOptionIds = resolveEffectiveProviderOptionIds(
      selectedProvider = providerEntry.bridge,
      selectedOptionIds = providerSelector.selectedOptionIds(providerEntry.bridge.provider),
      targetMode = currentTargetMode(),
      selectedThreadActivity = existingTaskController.selectedEntry()?.activity,
    )
    val effectivePlanModeEnabled = if (activeExtensionTab != null) {
      false
    }
    else {
      resolveEffectivePlanModeEnabled(
        selectedProvider = providerEntry.bridge,
        effectiveProviderOptionIds = effectiveProviderOptionIds,
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
        providerOptionIds = effectiveProviderOptionIds,
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
      AgentPromptLaunchError.CANCELLED,
      AgentPromptLaunchError.DROPPED_DUPLICATE,
      AgentPromptLaunchError.INTERNAL_ERROR,
        -> AgentPromptBundle.message("popup.error.launch.internal")
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

  private fun resolveContextProjectBasePath(launcher: AgentPromptLauncherBridge?): String? {
    val workingProjectPath = resolveWorkingProjectPath(launcher)
    if (workingProjectPath != null) {
      return workingProjectPath
    }
    return if (launcher == null) {
      project.basePath?.takeIf { path -> path.isNotBlank() }
    }
    else {
      null
    }
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
        refreshContextEntries(launcher)
        resolveExtensionTabs()
        updateTargetModeUi()
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
    val providerPrefs = launcherProvider()?.loadProviderPreferences() ?: AgentPromptLauncherBridge.ProviderPreferences()
    val contextRestoreSnapshot = uiStateService.loadContextRestoreSnapshot()
    val launcher = launcherProvider()

    setPromptAreaText(draft.promptText)
    val effectiveProviderOptions = providerPrefs.providerOptionsByProviderId.ifEmpty { draft.providerOptionsByProviderId }
    providerSelector.restoreProviderOptionSelections(effectiveProviderOptions)
    val persistedProvider = resolveRestoredPromptProvider(
      draftProviderId = providerPrefs.providerId ?: draft.providerId,
      preferredProvider = launcher?.preferredProvider(),
      availableProviders = providerSelector.availableProviders,
    )
    val restoredLaunchMode = providerPrefs.launchMode
    providerSelector.selectProvider(persistedProvider, restoredLaunchMode)
    selectedLaunchMode = providerSelector.selectedLaunchMode
    if (effectiveProviderOptions.isEmpty()) {
      providerSelector.applyLegacyPlanModeSelection(providerSelector.selectedProvider?.bridge?.provider, draft.planModeEnabled)
    }
    updateProviderOptionsVisibility()

    setTargetMode(draft.targetMode)
    existingTaskSearchQuery = draft.existingTaskSearch
    existingTaskController.selectedExistingTaskId = draft.selectedExistingTaskId
    removedAutoLogicalItemIds.clear()
    if (contextRestoreSnapshot.contextFingerprint == initialAutoContextFingerprint) {
      val normalizedRemovedIds = normalizeRemovedContextItemIds(contextRestoreSnapshot.removedContextItemIds)
      removedAutoLogicalItemIds.addAll(normalizedRemovedIds)
      val restoredEntries = applyDraftContextRemovals(
        entries = autoContextEntries,
        currentFingerprint = initialAutoContextFingerprint,
        draftFingerprint = contextRestoreSnapshot.contextFingerprint,
        removedLogicalItemIds = normalizedRemovedIds,
      )
      if (restoredEntries != autoContextEntries) {
        autoContextEntries = restoredEntries
      }
    }
    manualContextItemsBySourceId.clear()
    manualContextItemsBySourceId.putAll(contextRestoreSnapshot.manualContextItemsBySourceId)
    refreshContextEntries()
    resolveExtensionTabs()

    if (draft.targetMode == PromptTargetMode.EXISTING_TASK) {
      reloadExistingTasks()
    }

    return draft
  }

  private fun saveProviderPreferences() {
    launcherProvider()?.saveProviderPreferences(
      AgentPromptLauncherBridge.ProviderPreferences(
        providerId = providerSelector.selectedProvider?.bridge?.provider?.value,
        launchMode = providerSelector.selectedLaunchMode,
        providerOptionsByProviderId = providerSelector.providerOptionSelections(),
      )
    )
  }

  private fun saveDraft() {
    savePromptTextForSelectedTab()

    // Persist all tab texts so they survive popup close/reopen.
    val allTaskDrafts = LinkedHashMap<String, String>(taskPromptStates.size)
    taskPromptStates.forEach { (taskKey, state) ->
      allTaskDrafts[taskKey] = state.persistedUserText
    }

    uiStateService.saveDraft(
      AgentPromptUiDraft(
        promptText = allTaskDrafts[PromptTargetMode.NEW_TASK.name] ?: "",
        providerId = providerSelector.selectedProvider?.bridge?.provider?.value,
        targetMode = currentTargetMode(),
        sendMode = PromptSendMode.SEND_NOW,
        existingTaskSearch = existingTaskSearchQuery,
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        taskDrafts = allTaskDrafts,
        planModeEnabled = providerSelector.selectedProvider
                            ?.bridge
                            ?.provider
                            ?.let(providerSelector::selectedOptionIds)
                            ?.contains(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
                          ?: true,
        providerOptionsByProviderId = providerSelector.providerOptionSelections(),
      )
    )
    uiStateService.saveContextRestoreSnapshot(
      AgentPromptUiContextRestoreSnapshot(
        contextFingerprint = initialAutoContextFingerprint,
        removedContextItemIds = normalizeRemovedContextItemIds(removedAutoLogicalItemIds),
        manualContextItemsBySourceId = copyManualContextItemsBySourceId(),
      )
    )
  }

  private fun syncLivePromptTextForSelectedTab(promptText: String) {
    updateActiveTaskPromptState { state ->
      syncLivePromptTextForDraftState(state, promptText)
    }
  }

  private fun updateActiveTaskPromptState(update: (AgentPromptTaskDraftState) -> AgentPromptTaskDraftState) {
    val taskKey = activeTaskKey ?: return
    val currentState = taskPromptStates[taskKey] ?: restoredTaskPromptDraftState("")
    taskPromptStates[taskKey] = update(currentState)
  }

  private fun setPromptAreaText(promptText: String) {
    promptTextUpdateOrigin = PromptTextUpdateOrigin.PROGRAMMATIC
    try {
      promptArea.text = promptText
    }
    finally {
      promptTextUpdateOrigin = null
    }
  }

  private fun copyManualContextItemsBySourceId(): LinkedHashMap<String, List<AgentPromptContextItem>> {
    val copy = LinkedHashMap<String, List<AgentPromptContextItem>>(manualContextItemsBySourceId.size)
    manualContextItemsBySourceId.forEach { (sourceId, items) ->
      copy[sourceId] = ArrayList(items)
    }
    return copy
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
          selectedProvider = providerSelector.selectedProvider?.bridge,
          hasNextPromptTab = tabbedPane.selectedIndex in 0 until tabbedPane.tabCount - 1,
        )
      )
    }
    footerLabel.foreground = JBUI.CurrentTheme.Advertiser.foreground()
  }

  private fun refreshFooterHintForCurrentState() {
    if (shouldShowExistingTaskSelectionHint(
        targetMode = currentTargetMode(),
        selectedExistingTaskId = existingTaskController.selectedExistingTaskId,
        selectedProvider = providerSelector.selectedProvider?.bridge,
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
