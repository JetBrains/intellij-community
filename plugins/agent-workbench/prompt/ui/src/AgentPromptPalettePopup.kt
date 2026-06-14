// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.ide.FrameStateListener
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.Component
import java.awt.Window
import javax.swing.JPanel
import javax.swing.SwingUtilities

private const val ACTIVATION_CLICK_GRACE_MS = 500L

internal class AgentPromptPalettePopup(
  private val invocationData: AgentPromptInvocationData,
  private val initialAddContextRequest: AgentPromptAddContextRequest? = null,
  private val providersProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val launcherProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
  private val onClosed: (() -> Unit)? = null,
) : AgentPromptPalettePopupSession {
  private val project: Project = invocationData.project
  private val contextResolverService: AgentPromptContextResolverService = project.service()
  private val uiStateService: AgentPromptUiSessionStateService = project.service()
  private val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPalettePopup::class.java.classLoader)

  private val promptArea = AgentPromptTextField(
    project = project,
    completionProvider = AgentPromptClaudeSlashCompletionProvider(
      selectedProvider = ::selectedProviderForCompletion,
      resolveWorkingProjectPaths = ::resolveWorkingProjectPathsForCompletion,
      resolveCodexSkillEntries = ::resolveCodexSkillEntriesForCompletion,
    ),
  )

  @Suppress("RAW_SCOPE_CREATION")
  private val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)
  private val popupDisposable = Disposer.newDisposable("AgentPromptPalettePopup")

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private var isExplicitCloseInProgress: Boolean = false

  @Volatile
  private var lastSourceFrameActivationMs: Long = 0L
  private lateinit var providerSelector: AgentPromptProviderSelector
  private lateinit var sessionController: AgentPromptPaletteSessionController

  override fun show() {
    val content = createContentPanel()
    sessionController.initialize(initialAddContextRequest)

    val createdPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content, promptArea)
      .setProject(project)
      .setModalContext(false)
      .setCancelOnClickOutside(true)
      .setCancelCallback {
        shouldAllowPromptPopupCancellation(
          popupProject = project,
          isRecentSourceFrameActivation =
            System.currentTimeMillis() - lastSourceFrameActivationMs < ACTIVATION_CLICK_GRACE_MS,
          currentEvent = IdeEventQueue.getInstance().trueCurrentEvent,
          isExplicitClose = isExplicitCloseInProgress,
          resolveProject = ::resolveProjectForComponent,
          autoClose = uiStateService.autoClose
        )
      }
      .setCancelOnWindowDeactivation(false)
      .setRequestFocus(true)
      .setCancelKeyEnabled(true)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, "AgentWorkbench.PromptPalette", true)
      .setMinSize(AGENT_PROMPT_PALETTE_MINIMUM_SIZE)
      .setLocateWithinScreenBounds(false)
      .createPopup()

    popup = createdPopup
    popupActive = true
    installFrameActivationRefocusListener(createdPopup)
    createdPopup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        popupActive = false
        popup = null
        sessionController.onPopupClosed()
        Disposer.dispose(popupDisposable)
        popupScope.cancel("Agent prompt popup closed")
        onClosed?.invoke()
      }
    })

    sessionController.installHandlers()
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

  override fun requestComposerFocus() {
    val currentPopup = popup ?: return
    if (!currentPopup.isVisible) {
      return
    }

    currentPopup.setRequestFocus(true)
    IdeFocusManager.getInstance(project).requestFocusInProject(promptArea, project)
  }

  override fun isVisible(): Boolean {
    return popup?.isVisible == true
  }

  override fun applyAddContext(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult {
    if (!::sessionController.isInitialized) {
      return AgentPromptAddContextApplyResult.ALREADY_ADDED
    }
    return sessionController.applyAddContextRequest(request)
  }

  private fun installFrameActivationRefocusListener(createdPopup: JBPopup) {
    project.messageBus.connect(createdPopup).subscribe(FrameStateListener.TOPIC, object : FrameStateListener {
      override fun onFrameActivated(ideFrame: IdeFrame) {
        if (ideFrame.project === project) {
          lastSourceFrameActivationMs = System.currentTimeMillis()
        }
        if (shouldRefocusPromptOnFrameActivated(
            popupProject = project,
            activatedProject = ideFrame.project,
            isPopupVisible = isVisible(),
          )
        ) {
          requestFocus()
        }
      }
    })
  }

  private fun createContentPanel(): JPanel {
    lateinit var controllerRef: AgentPromptPaletteSessionController
    val suggestions = AgentPromptSuggestionsComponent { candidate -> controllerRef.applySuggestedPrompt(candidate) }
    val contextChips = AgentPromptContextChipsComponent { entry -> controllerRef.removeContextEntry(entry) }
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestions.component,
      contextChipsPanel = contextChips.component,
      pinned = { controllerRef.isPinned },
      onPromptLibraryClicked = { controllerRef.showPromptLibraryChooser() },
      onExistingTaskSelected = { selected -> controllerRef.onExistingTaskSelected(selected) },
      onPinClicked = { controllerRef.togglePin() }
    )
    providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      headerControls = view.headerControls,
      providersProvider = providersProvider,
      sessionsMessageResolver = sessionsMessageResolver,
      asyncRefreshScope = popupScope,
      onProviderOptionsChanged = { controllerRef.onProviderOptionsChanged() },
      onProviderSelectionChanged = { controllerRef.onProviderSelectionChanged() },
    )
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = view.existingTaskListModel,
      existingTaskList = view.existingTaskList,
      popupScope = popupScope,
      sessionsMessageResolver = sessionsMessageResolver,
      onStateChanged = { controllerRef.onExistingTaskStateChanged() },
    )
    val suggestionController = AgentPromptSuggestionController(
      popupScope = popupScope,
      onSuggestionsUpdated = suggestions::render,
    )
    sessionController = AgentPromptPaletteSessionController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      view = view,
      contextChips = contextChips,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      suggestionController = suggestionController,
      contextResolverService = contextResolverService,
      uiStateService = uiStateService,
      launcherProvider = launcherProvider,
      closePopup = ::cancelPopupExplicitly,
      isPopupActive = { popupActive },
      movePopupToFitScreen = { popup?.moveToFitScreen() },
      popupScope = popupScope,
      parentDisposable = popupDisposable,
    )
    controllerRef = sessionController
    return view.rootPanel
  }

  private fun selectedProviderForCompletion(): AgentSessionProvider? {
    return if (::providerSelector.isInitialized) providerSelector.selectedProvider?.bridge?.provider else null
  }

  private fun resolveWorkingProjectPathsForCompletion(): List<String> {
    val sourceProjectBasePath = launcherProvider()
      ?.resolveSourceProject(invocationData)
      ?.basePath
    if (::sessionController.isInitialized) {
      return resolveClaudeSlashCompletionProjectPaths(
        workingProjectPath = sessionController.resolveWorkingProjectPath(),
        sourceProjectBasePath = sourceProjectBasePath,
        projectBasePath = project.basePath,
      )
    }
    return resolveClaudeSlashCompletionProjectPaths(
      workingProjectPath = launcherProvider()
        ?.resolveWorkingProjectPath(invocationData)
        ?.takeIf { path -> path.isNotBlank() },
      sourceProjectBasePath = sourceProjectBasePath,
      projectBasePath = project.basePath,
    )
  }

  private fun resolveCodexSkillEntriesForCompletion(): List<AgentPromptReusableSourceEntry> {
    return if (::sessionController.isInitialized) sessionController.codexSkillCompletionEntriesForCompletion() else emptyList()
  }

  private fun cancelPopupExplicitly() {
    isExplicitCloseInProgress = true
    try {
      popup?.cancel()
    }
    finally {
      isExplicitCloseInProgress = false
    }
  }
}

internal fun resolveProjectForComponent(component: Component?): Project? {
  if (component == null) return null
  var window: Window? = SwingUtilities.getWindowAncestor(component) ?: component as? Window
  while (window != null) {
    if (window is IdeFrame) {
      return window.project
    }
    window = window.owner
  }
  return null
}

internal fun resolveClaudeSlashCompletionProjectPaths(
  workingProjectPath: String?,
  sourceProjectBasePath: String?,
  projectBasePath: String?,
): List<String> {
  return buildList {
    workingProjectPath?.takeIf { path -> path.isNotBlank() }?.let(::add)
    sourceProjectBasePath?.takeIf { path -> path.isNotBlank() }?.let(::add)
    projectBasePath?.takeIf { path -> path.isNotBlank() }?.let(::add)
  }.distinct()
}
