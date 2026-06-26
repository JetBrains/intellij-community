// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/core/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.ide.FrameStateListener
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private var isExplicitCloseInProgress: Boolean = false

  @Volatile
  private var lastSourceFrameActivationMs: Long = 0L
  private lateinit var content: AgentPromptPaletteContent

  override fun show() {
    content = createAgentPromptPaletteContent(
      invocationData = invocationData,
      contextResolverService = contextResolverService,
      uiStateService = uiStateService,
      sessionsMessageResolver = sessionsMessageResolver,
      providersProvider = providersProvider,
      launcherProvider = launcherProvider,
      closeHost = ::cancelPopupExplicitly,
      isHostActive = { popupActive },
      revalidateHost = { popup?.moveToFitScreen() },
    )
    content.sessionController.initialize(initialAddContextRequest)

    val createdPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content.rootPanel, content.promptArea)
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
        content.dispose("Agent prompt popup closed")
        onClosed?.invoke()
      }
    })

    content.sessionController.installHandlers()
    createdPopup.showCenteredInCurrentWindow(project)
  }

  override fun requestFocus() {
    val currentPopup = popup ?: return
    if (!currentPopup.isVisible) {
      return
    }

    currentPopup.setRequestFocus(true)
    val focusComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(currentPopup.content) ?: content.promptArea
    IdeFocusManager.getInstance(project).requestFocusInProject(focusComponent, project)
  }

  override fun requestComposerFocus() {
    val currentPopup = popup ?: return
    if (!currentPopup.isVisible) {
      return
    }

    currentPopup.setRequestFocus(true)
    IdeFocusManager.getInstance(project).requestFocusInProject(content.promptArea, project)
  }

  override fun isVisible(): Boolean {
    return popup?.isVisible == true
  }

  override fun applyAddContext(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult {
    if (!::content.isInitialized) {
      return AgentPromptAddContextApplyResult.ALREADY_ADDED
    }
    return content.sessionController.applyAddContextRequest(request)
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

internal class AgentPromptPaletteContent(
  @JvmField val rootPanel: JPanel,
  @JvmField val promptArea: AgentPromptTextField,
  @JvmField val view: AgentPromptPaletteView,
  @JvmField val providerSelector: AgentPromptProviderSelector,
  @JvmField val existingTaskController: AgentPromptExistingTaskController,
  @JvmField val sessionController: AgentPromptPaletteSessionController,
  @JvmField val sessionScope: CoroutineScope,
  @JvmField val imageDropSupportScope: CoroutineScope,
  @JvmField val swingDisposable: Disposable,
) {
  private var disposed: Boolean = false

  fun dispose(reason: String) {
    if (disposed) {
      return
    }
    disposed = true
    imageDropSupportScope.cancel(reason)
    try {
      sessionController.onHostClosed()
    }
    finally {
      try {
        view.headerToolbar.targetComponent = null
        view.footerPinToolbar.targetComponent = null
        rootPanel.parent?.remove(rootPanel)
      }
      finally {
        try {
          Disposer.dispose(swingDisposable)
        }
        finally {
          sessionScope.cancel(reason)
        }
      }
    }
  }
}

internal enum class AgentPromptPaletteHostMode {
  POPUP,
  INLINE_EMPTY_STATE,
  INLINE_NEW_THREAD,
}

internal val AgentPromptPaletteHostMode.isInlinePrompt: Boolean
  get() = this == AgentPromptPaletteHostMode.INLINE_EMPTY_STATE || this == AgentPromptPaletteHostMode.INLINE_NEW_THREAD

internal fun createAgentPromptPaletteContent(
  invocationData: AgentPromptInvocationData,
  contextResolverService: AgentPromptContextResolverService,
  uiStateService: AgentPromptUiSessionStateService,
  sessionsMessageResolver: AgentPromptSessionsMessageResolver,
  providersProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  launcherProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
  closeHost: () -> Unit,
  isHostActive: () -> Boolean,
  revalidateHost: () -> Unit,
  hostMode: AgentPromptPaletteHostMode = AgentPromptPaletteHostMode.POPUP,
  sessionScope: CoroutineScope = createAgentPromptPaletteSessionScope(),
): AgentPromptPaletteContent {
  val project = invocationData.project
  var providerSelectorRef: AgentPromptProviderSelector? = null
  var sessionControllerRef: AgentPromptPaletteSessionController? = null
  val swingDisposable = Disposer.newDisposable("AgentPromptPaletteContent.swing")
  val imageDropSupportScope = createAgentPromptImageDropSupportScope(sessionScope)
  try {
    val promptArea = AgentPromptTextField(
      project = project,
      completionProvider = AgentPromptClaudeSlashCompletionProvider(
        selectedProvider = { providerSelectorRef?.selectedProvider?.bridge?.provider },
        resolveWorkingProjectPaths = {
          resolveWorkingProjectPathsForCompletion(
            project = project,
            invocationData = invocationData,
            launcherProvider = launcherProvider,
            sessionController = sessionControllerRef,
          )
        },
        resolveCodexSkillEntries = { sessionControllerRef?.codexSkillCompletionEntriesForCompletion().orEmpty() },
      ),
    )
    promptArea.setDisposedWith(swingDisposable)

    val suggestions = AgentPromptSuggestionsComponent { candidate -> sessionControllerRef?.applySuggestedPrompt(candidate) }
    val contextChips = AgentPromptContextChipsComponent { entry -> sessionControllerRef?.removeContextEntry(entry) }
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestions.component,
      contextChipsPanel = contextChips.component,
      pinned = { sessionControllerRef?.isPinned == true },
      onPromptLibraryClicked = { sessionControllerRef?.showPromptLibraryChooser() },
      onExistingTaskSelected = { selected -> sessionControllerRef?.onExistingTaskSelected(selected) },
      onPinClicked = { sessionControllerRef?.togglePin() },
      hostMode = hostMode,
    )
    val providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      headerControls = view.headerControls,
      providersProvider = providersProvider,
      sessionsMessageResolver = sessionsMessageResolver,
      asyncRefreshScope = sessionScope,
      onProviderOptionsChanged = { sessionControllerRef?.onProviderOptionsChanged() },
      onProviderSelectionChanged = { sessionControllerRef?.onProviderSelectionChanged() },
    )
    providerSelectorRef = providerSelector
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = view.existingTaskListModel,
      existingTaskList = view.existingTaskList,
      sessionScope = sessionScope,
      sessionsMessageResolver = sessionsMessageResolver,
      onStateChanged = { sessionControllerRef?.onExistingTaskStateChanged() },
    )
    val suggestionController = AgentPromptSuggestionController(
      sessionScope = sessionScope,
      onSuggestionsUpdated = suggestions::render,
    )
    val sessionController = AgentPromptPaletteSessionController(
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
      closeHost = closeHost,
      isHostActive = isHostActive,
      revalidateHost = revalidateHost,
      hostMode = hostMode,
      sessionScope = sessionScope,
      imageDropSupportScope = imageDropSupportScope,
    )
    sessionControllerRef = sessionController
    return AgentPromptPaletteContent(
      rootPanel = view.rootPanel,
      promptArea = promptArea,
      view = view,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      sessionController = sessionController,
      sessionScope = sessionScope,
      imageDropSupportScope = imageDropSupportScope,
      swingDisposable = swingDisposable,
    )
  }
  catch (error: Throwable) {
    try {
      imageDropSupportScope.cancel("Agent prompt palette content creation failed", error)
    }
    finally {
      Disposer.dispose(swingDisposable)
    }
    throw error
  }
}

@Suppress("RAW_SCOPE_CREATION")
private fun createAgentPromptImageDropSupportScope(sessionScope: CoroutineScope): CoroutineScope {
  val parentJob = sessionScope.coroutineContext[Job]
  return CoroutineScope(sessionScope.coroutineContext + SupervisorJob(parentJob) + CoroutineName("Agent prompt image drop support"))
}

private fun resolveWorkingProjectPathsForCompletion(
  project: Project,
  invocationData: AgentPromptInvocationData,
  launcherProvider: () -> AgentPromptLauncherBridge?,
  sessionController: AgentPromptPaletteSessionController?,
): List<String> {
  val launcher = launcherProvider()
  val sourceProjectBasePath = launcher
    ?.resolveSourceProject(invocationData)
    ?.basePath
  val workingProjectPath = sessionController
                             ?.resolveWorkingProjectPath()
                           ?: launcher
                             ?.resolveWorkingProjectPath(invocationData)
                             ?.takeIf { path -> path.isNotBlank() }
  return resolveClaudeSlashCompletionProjectPaths(
    workingProjectPath = workingProjectPath,
    sourceProjectBasePath = sourceProjectBasePath,
    projectBasePath = project.basePath,
  )
}

@Suppress("RAW_SCOPE_CREATION")
private fun createAgentPromptPaletteSessionScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

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
