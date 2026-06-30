// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

// @spec plugins/ij-air/spec/thread-view/agent-thread-view.spec.md

import com.intellij.CommonBundle
import com.intellij.agent.workbench.ui.AgentWorkbenchActionIds
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaces
import com.intellij.platform.ai.agent.sessions.core.launch.effectiveAgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionLaunchProfileResolver
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionResolvedLaunchProfile
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.wm.StatusBar
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextArea
import javax.swing.Timer
import kotlin.coroutines.resume

private const val DEFERRED_START_PROGRESS_DELAY_MS = 300
private const val DEFERRED_START_PROGRESS_NAME = "Agent Thread View Start Progress"
private const val DEFERRED_START_PROGRESS_TIMER_PROPERTY = "AgentThreadViewFileEditor.deferredStartProgressTimer"

internal class AgentThreadViewFileEditor(
  private val project: Project,
  private val file: AgentThreadViewVirtualFile,
  private val terminalTabs: AgentThreadViewTerminalTabs = ToolWindowAgentThreadViewTerminalTabs,
  private val liveTerminalRegistry: AgentThreadViewLiveTerminalRegistry? = null,
  private val tabSnapshotWriter: AgentThreadViewTabSnapshotWriter = ApplicationAgentThreadViewTabSnapshotWriter,
  private val archivedRestoreHandler: AgentThreadViewArchivedRestoreHandler = ApplicationAgentThreadViewArchivedRestoreHandler,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
  editorCoroutineScope: CoroutineScope? = null,
  private val providerDescriptorResolver: (AgentSessionProvider) -> AgentSessionProviderDescriptor? = AgentSessionProviders::find,
  private val customContentProviderResolver: (AgentThreadViewContentContext) -> AgentThreadViewCustomContentProvider? = AgentThreadViewCustomContent::find,
  private val behaviorResolver: (AgentSessionProvider?) -> AgentThreadViewProviderBehavior = ::resolveAgentThreadViewProviderBehavior,
) : UserDataHolderBase(), FileEditor {
  private val ownedTerminalStartupJob = if (editorCoroutineScope == null) SupervisorJob() else null

  @Suppress("RAW_SCOPE_CREATION")
  private val terminalStartupScope = editorCoroutineScope ?: CoroutineScope(checkNotNull(ownedTerminalStartupJob) + Dispatchers.Default)
  private val component = AgentThreadViewFileEditorComponent()

  private fun buildEditorTabActions(): ActionGroup? {
    val actionManager = ActionManager.getInstance()
    val providerActionIds = buildList {
      if (providerDescriptor?.supportsPendingEditorTabRebind == true) {
        add(AgentWorkbenchActionIds.Sessions.BIND_PENDING_AGENT_THREAD_FROM_EDITOR_TAB)
      }
    }
    val actions = buildList {
      providerActionIds.forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
    }
    return buildAgentThreadViewEditorTabActionGroup(actions)
  }

  private var cachedTabActionsProvider: AgentSessionProvider? = null
  private var cachedTabActions: ActionGroup? = null
  private var cachedTabActionsInitialized: Boolean = false
  private var tab: AgentThreadViewTerminalTab? = null
  private var pendingContextPanel: AgentThreadViewPendingContextPanel? = null
  private var pendingContextPanelInstalled: Boolean = false
  private var initializationStarted: Boolean = false
  private var initializationRequested: Boolean = false
  private var customContentInstalled: Boolean = false
  private var customContentPreferredFocusedComponent: JComponent? = null
  private var focusTerminalAfterInitialization: Boolean = false
  private var stateApplied: Boolean = file.projectPath.isNotBlank() || file.threadIdentity.isNotBlank()
  private var initializationJob: Job? = null
  private var disposed: Boolean = false
  private var pendingThreadRefreshController: AgentThreadViewPendingThreadRefreshController? = null
  private var terminalTitleThreadRebindController: AgentThreadViewDisposableController? = null
  private var concreteThreadRebindController: AgentThreadViewConcreteThreadRebindController? = null
  private var initialMessageDispatcher: AgentThreadViewInitialMessageDispatcher? = null
  private var scopedTerminalRefreshController: AgentThreadViewDisposableController? = null
  private var terminalRestoreContextController: AgentThreadViewDisposableController? = null
  private var crossProjectDockTargetRegistration: Disposable? = null

  private val providerDescriptor
    get() = file.provider?.let(providerDescriptorResolver)

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    file.deferredStartContent()?.preferredFocusedComponent?.let { return it }
    customContentPreferredFocusedComponent?.let { return it }
    return tab?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = AgentThreadViewBundle.message("thread.view.filetype.name")

  override fun getTabActions(): ActionGroup? {
    val provider = file.provider
    if (!cachedTabActionsInitialized || cachedTabActionsProvider != provider) {
      cachedTabActionsProvider = provider
      cachedTabActions = buildEditorTabActions()
      cachedTabActionsInitialized = true
    }
    return cachedTabActions
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (!file.shouldRestoreOnRestart() || file.projectPath.isBlank() || file.threadIdentity.isBlank()) {
      return AgentThreadViewFileEditorState(snapshot = null)
    }
    return AgentThreadViewFileEditorState(snapshot = file.toSnapshot(), startupIntent = file.startupIntent())
  }

  override fun setState(state: FileEditorState) {
    val threadViewState = state as? AgentThreadViewFileEditorState ?: return
    stateApplied = true
    val providerBeforeUpdate = file.provider
    val snapshot = threadViewState.snapshot
    if (snapshot != null) {
      file.updateRestoreOnRestart(true)
      file.updateFromResolution(AgentThreadViewTabResolution.Resolved(snapshot))
      file.updateStartupIntent(threadViewState.startupIntent)
      ensureCrossProjectDockTargetRegistration()
      FileEditorManager.getInstance(project).updateFilePresentation(file)
    }
    else {
      file.updateRestoreOnRestart(false)
      file.updateStartupIntent(null)
    }
    if (file.provider != providerBeforeUpdate) {
      invalidateTabActionsCache()
    }
    if (initializationRequested) {
      ensureInitialized()
    }
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = !disposed

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun getFile(): AgentThreadViewVirtualFile = file

  override fun selectNotify() {
    if (tab == null) {
      focusTerminalAfterInitialization = true
    }
    ensureInitialized()
  }

  override fun deselectNotify() {
    focusTerminalAfterInitialization = false
  }

  override fun dispose() {
    disposed = true
    focusTerminalAfterInitialization = false
    initializationJob?.cancel()
    initializationJob = null
    ownedTerminalStartupJob?.cancel()
    component.cancelAwaitShowing()
    crossProjectDockTargetRegistration?.let(Disposer::dispose)
    crossProjectDockTargetRegistration = null
    disposeTerminalAttachments(clearPendingContextPanel = true)
    pendingContextPanel = null
    pendingContextPanelInstalled = false
    file.clearDeferredStartContent()
    disposeDeferredStartProgressTimers(component)
    component.removeAll()
  }

  private fun ensureInitialized() {
    initializationRequested = true
    if (disposed) {
      return
    }
    if (!stateApplied && file.projectPath.isBlank() && file.threadIdentity.isBlank()) {
      return
    }
    val deferredStartState = file.deferredStartState
    if (shouldBlockTerminalInitialization(deferredStartState)) {
      renderDeferredStartState(checkNotNull(deferredStartState))
      return
    }
    if (tryInstallCustomContent()) {
      return
    }
    if (initializationStarted) {
      return
    }
    val validationError = validateAgentThreadViewFile(file)
    if (validationError != null) {
      handleRestoreValidationError(validationError)
      return
    }
    initializationStarted = true
    val startupLaunchSpecOverride = file.consumeStartupLaunchSpecOverride()
    val suppressInitialMessageDispatch = startupLaunchSpecOverride != null && file.consumeSuppressInitialMessageDispatchOnStartup()
    val startupIntent = file.startupIntent()
    if (startupLaunchSpecOverride == null && file.isPendingThread && startupIntent == null) {
      handleRestoreValidationError(AgentThreadViewBundle.message("thread.view.restore.validation.pending.thread"))
      return
    }
    initializationJob = terminalStartupScope.launch {
      try {
        awaitEditorComponentShowing()
        if (startupLaunchSpecOverride == null && startupIntent == null && isRestoredArchivedThread(providerDescriptor)) {
          file.updateRestoreOnRestart(false)
          archivedRestoreHandler.closeAndForget(file)
          return@launch
        }
        val startupLaunchSpec = startupLaunchSpecOverride ?: resolveStartupLaunchSpec(startupIntent)
        val resolvedRegistry = resolveLiveTerminalRegistry()
        attachTerminal(resolvedRegistry, startupLaunchSpec, suppressInitialMessageDispatch)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        AgentThreadViewRestoreNotificationService.reportTerminalInitializationFailure(project, file, e)
      }
    }
  }

  /**
   * Installs provider-supplied non-terminal content (e.g. the ACP screen) into the same
   * editor tab and skips the terminal lifecycle. Returns true when custom content owns this tab.
   */
  private fun tryInstallCustomContent(): Boolean {
    if (customContentInstalled) {
      return true
    }
    val contentContext = resolveCustomContentContext() ?: return false
    val contentProvider = customContentProviderResolver(contentContext) ?: return false
    val deferredStartState = file.deferredStartState
    if (deferredStartState?.phase == AgentThreadViewDeferredStartPhase.READY_TO_START) {
      file.updateDeferredStartState(null)
    }
    customContentInstalled = true
    initializationStarted = true
    ensureCrossProjectDockTargetRegistration()
    val customComponent = contentProvider.createComponent(
      project = project,
      context = contentContext,
      parent = this,
    )
    customContentPreferredFocusedComponent = (customComponent as? AgentThreadViewPreferredFocusableContent)?.preferredFocusedComponent
    disposeDeferredStartProgressTimers(component)
    component.removeAll()
    component.add(customComponent, BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
    file.updateStartupIntent(null)
    return true
  }

  private suspend fun isRestoredArchivedThread(descriptor: AgentSessionProviderDescriptor?): Boolean {
    val source = descriptor?.sessionSource as? AgentSessionArchivedSource ?: return false
    val archivedThreads = try {
      source.listArchivedThreads(path = file.projectPath, openProject = project)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: Throwable) {
      return false
    }
    return archivedThreads.any { thread -> thread.matchesRestoredAgentThreadViewFile(file) }
  }

  private suspend fun awaitEditorComponentShowing() {
    if (liveTerminalRegistry == null) {
      withContext(Dispatchers.EDT) {
        component.awaitShowing()
      }
    }
    else {
      component.awaitShowing()
    }
  }

  private suspend fun resolveLiveTerminalRegistry(): AgentThreadViewLiveTerminalRegistry {
    return liveTerminalRegistry ?: project.serviceAsync<AgentThreadViewLiveTerminalRegistryService>()
  }

  private suspend fun resolveStartupLaunchSpec(startupIntent: AgentThreadViewStartupIntent?): AgentSessionTerminalLaunchSpec {
    return when (startupIntent) {
      is AgentThreadViewStartupIntent.NewSession -> resolveNewSessionLaunchSpec(startupIntent)
      null -> resolveResumeLaunchSpec()
    }
  }

  private suspend fun resolveNewSessionLaunchSpec(startupIntent: AgentThreadViewStartupIntent.NewSession): AgentSessionTerminalLaunchSpec {
    val resolvedLaunchProfile = resolveLaunchProfile(
      launchProfileId = startupIntent.launchProfileId,
      requiredProvider = startupIntent.provider,
    )
    val provider = resolvedLaunchProfile?.provider ?: startupIntent.provider
    val launchMode = resolvedLaunchProfile?.launchMode ?: startupIntent.launchMode
    val launchTargetId = resolvedLaunchProfile?.launchTargetId ?: startupIntent.launchTargetId
    val generationSettings = resolvedLaunchProfile?.generationSettings ?: file.generationSettings
    val descriptor = AgentSessionProviders.find(provider)
                     ?: throw IllegalStateException("Missing Agent Thread View provider for ${provider.value}")
    if (launchMode !in descriptor.supportedLaunchModes) {
      throw IllegalStateException("Unsupported Agent Thread View launch mode $launchMode for ${provider.value}")
    }
    return AgentSessionLaunchPlanner.plan(
      intent = AgentSessionLaunchIntent(
        projectPath = file.projectPath,
        projectDirectory = file.projectDirectory,
        provider = provider,
        operation = AgentSessionLaunchOperation.NEW,
        launchMode = launchMode,
        launchTargetId = launchTargetId,
        surfaceId = resolvedLaunchProfile?.surfaceId ?: startupIntent.surfaceId,
        generationSettings = generationSettings,
      ),
      project = project,
    ).launchSpec
  }

  private suspend fun resolveResumeLaunchSpec(): AgentSessionTerminalLaunchSpec {
    val provider = file.provider ?: throw IllegalStateException("Missing Agent Thread View provider for ${file.url}")
    val resolvedLaunchProfile = resolveLaunchProfile(
      launchProfileId = file.launchProfileId,
      requiredProvider = provider,
    )
    val launchTargetId = resolvedLaunchProfile?.launchTargetId ?: file.launchTargetId
    val surfaceId = resolvedLaunchProfile?.surfaceId ?: parseAgentThreadViewSurfaceId(file.surfaceId)
    return AgentSessionLaunchPlanner.plan(
      intent = AgentSessionLaunchIntent(
        projectPath = file.projectPath,
        projectDirectory = file.projectDirectory,
        provider = provider,
        operation = AgentSessionLaunchOperation.RESUME,
        sessionId = file.threadId.ifBlank { file.sessionId },
        launchMode = resolvedLaunchProfile?.launchMode ?: parseAgentThreadViewLaunchMode(file.launchMode),
        launchTargetId = launchTargetId,
        surfaceId = surfaceId,
        generationSettings = resolvedLaunchProfile?.generationSettings ?: file.generationSettings,
      ),
      project = project,
    ).launchSpec
  }

  private fun resolveLaunchProfile(
    launchProfileId: String?,
    requiredProvider: AgentSessionProvider,
  ): AgentSessionResolvedLaunchProfile? {
    return serviceOrNull<AgentSessionLaunchProfileResolver>()?.resolveLaunchProfile(
      launchProfileId = launchProfileId,
      requiredProvider = requiredProvider,
    )
  }

  private fun resolveCustomContentContext(): AgentThreadViewContentContext? {
    val provider = file.provider ?: return null
    val startupIntent = file.startupIntent() as? AgentThreadViewStartupIntent.NewSession
    val launchProfileId = startupIntent?.launchProfileId ?: file.launchProfileId
    val resolvedLaunchProfile = resolveLaunchProfile(
      launchProfileId = launchProfileId,
      requiredProvider = provider,
    )
    val requestedSurfaceId = resolvedLaunchProfile?.surfaceId ?: startupIntent?.surfaceId ?: parseAgentThreadViewSurfaceId(file.surfaceId)
    val surfaceId = providerDescriptorResolver(provider)?.let { descriptor ->
      effectiveAgentSessionSurfaceId(descriptor, requestedSurfaceId)
    } ?: requestedSurfaceId ?: AgentSessionSurfaces.TERMINAL
    return AgentThreadViewContentContext(
      provider = provider,
      surfaceId = surfaceId,
      launchTargetId = resolvedLaunchProfile?.launchTargetId ?: startupIntent?.launchTargetId ?: file.launchTargetId,
      threadIdentity = file.threadIdentity,
      threadId = file.threadId.ifBlank { file.threadIdentity },
    )
  }

  private suspend fun attachTerminal(
    liveTerminalRegistry: AgentThreadViewLiveTerminalRegistry,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
    suppressInitialMessageDispatch: Boolean,
  ) {
    if (this.liveTerminalRegistry == null) {
      withContext(Dispatchers.EDT) {
        attachTerminalOnEdt(liveTerminalRegistry, startupLaunchSpec, suppressInitialMessageDispatch)
      }
    }
    else {
      attachTerminalOnEdt(liveTerminalRegistry, startupLaunchSpec, suppressInitialMessageDispatch)
    }
  }

  private fun attachTerminalOnEdt(
    liveTerminalRegistry: AgentThreadViewLiveTerminalRegistry,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
    suppressInitialMessageDispatch: Boolean = false,
  ) {
    if (disposed || tab != null) {
      return
    }
    ensureCrossProjectDockTargetRegistration()
    val deferredStartState = file.deferredStartState
    if (deferredStartState?.phase == AgentThreadViewDeferredStartPhase.READY_TO_START) {
      file.updateDeferredStartState(null)
    }
    file.clearDeferredStartContent()
    val behavior = behaviorResolver(file.provider)
    val createdTab = liveTerminalRegistry.acquireOrCreate(
      file = file,
      terminalTabs = terminalTabs,
      startupLaunchSpec = startupLaunchSpec,
    )
    tab = createdTab
    file.updateStartupIntent(null)
    if (suppressInitialMessageDispatch) {
      file.markInitialPromptDelivered(AgentInitialPromptDeliveryChannel.STARTUP_COMMAND)
    }
    if (file.isPendingThread) {
      file.updateRestoreOnRestart(false)
    }
    val pendingController = AgentThreadViewPendingThreadRefreshController(
      file = file,
      behavior = behavior,
      tabSnapshotWriter = tabSnapshotWriter,
      currentTimeProvider = currentTimeProvider,
      retryIntervalMs = pendingScopedRefreshRetryIntervalMs,
    )
    pendingThreadRefreshController = pendingController
    val concreteController = AgentThreadViewConcreteThreadRebindController(
      file = file,
      behavior = behavior,
      tabSnapshotWriter = tabSnapshotWriter,
      currentTimeProvider = currentTimeProvider,
    )
    concreteThreadRebindController = concreteController
    val messageDispatcher = AgentThreadViewInitialMessageDispatcher(
      project = project,
      file = file,
      behavior = behavior,
      descriptor = providerDescriptor,
      tabSnapshotWriter = tabSnapshotWriter,
    )
    initialMessageDispatcher = messageDispatcher
    pendingController.attach(createdTab)
    concreteController.attach(createdTab, providerDescriptor)
    if (!suppressInitialMessageDispatch) {
      messageDispatcher.schedule(createdTab)
    }
    scopedTerminalRefreshController = createAgentThreadViewScopedTerminalRefreshController(file, createdTab, providerDescriptor)
    val restoreContextController = AgentThreadViewTerminalRestoreContextController(
      file = file,
      descriptor = providerDescriptor,
      parentDisposable = this,
    )
    terminalRestoreContextController = restoreContextController
    restoreContextController.attach(createdTab)
    terminalTitleThreadRebindController = createAgentThreadViewTerminalTitleThreadRebindController(
      file = file,
      tab = createdTab,
      tabSnapshotWriter = tabSnapshotWriter,
    )
    installPendingContextInterceptor(createdTab)
    disposeDeferredStartProgressTimers(component)
    component.removeAll()
    pendingContextPanelInstalled = false
    component.add(createdTab.component, BorderLayout.CENTER)
    installAgentThreadViewTerminalFileDropSupport(createdTab.component, createdTab, this)
    pendingContextPanel?.let(::ensurePendingContextPanelInstalled)
    component.revalidate()
    component.repaint()
    focusTerminalIfRequested(createdTab)
  }

  private fun focusTerminalIfRequested(createdTab: AgentThreadViewTerminalTab) {
    if (!focusTerminalAfterInitialization) {
      return
    }
    focusTerminalAfterInitialization = false
    createdTab.preferredFocusableComponent.requestFocusInWindow()
  }

  private fun getOrCreatePendingContextPanel(): AgentThreadViewPendingContextPanel {
    pendingContextPanel?.let {
      return it
    }
    return AgentThreadViewPendingContextPanel(file.projectPath).also { panel ->
      pendingContextPanel = panel
    }
  }

  private fun ensurePendingContextPanelInstalled(panel: AgentThreadViewPendingContextPanel) {
    if (pendingContextPanelInstalled) {
      return
    }
    component.add(panel.component, BorderLayout.SOUTH)
    installAgentThreadViewContextFileDropSupport(panel.component, ::addPendingContextItems, this)
    pendingContextPanelInstalled = true
    component.revalidate()
    component.repaint()
  }

  private fun ensureCrossProjectDockTargetRegistration() {
    if (crossProjectDockTargetRegistration == null && file.projectPath.isNotBlank()) {
      crossProjectDockTargetRegistration = AgentThreadViewCrossProjectDockTargetRegistrar().register(project, file)
    }
  }

  private fun handleRestoreValidationError(validationError: String) {
    if (file.projectPath.isBlank() && file.threadIdentity.isBlank()) {
      if (!project.isDisposed) {
        FileEditorManager.getInstance(project).closeFile(file)
      }
      return
    }
    forgetAgentThreadViewTabMetadata(file.tabKey)
    AgentThreadViewRestoreNotificationService.reportRestoreFailure(project, file, validationError)
    if (!project.isDisposed) {
      FileEditorManager.getInstance(project).closeFile(file)
    }
  }

  internal fun refreshForFileStateChange() {
    if (disposed) {
      return
    }
    val deferredStartState = file.deferredStartState
    if (tab == null && shouldBlockTerminalInitialization(deferredStartState)) {
      renderDeferredStartState(checkNotNull(deferredStartState))
      return
    }
    ensureInitialized()
  }

  internal suspend fun restartForFileStateChange(
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
    replaceRetainedTerminal: Boolean,
  ): Boolean {
    if (disposed) {
      return false
    }
    val initializedTab = tab
    if (initializedTab == null) {
      file.setStartupLaunchSpecOverride(startupLaunchSpec)
      refreshForFileStateChange()
      return false
    }
    val resolvedRegistry = resolveLiveTerminalRegistry()
    return if (this.liveTerminalRegistry == null) {
      withContext(Dispatchers.EDT) {
        restartTerminalOnEdt(resolvedRegistry, startupLaunchSpec, replaceRetainedTerminal)
      }
    }
    else {
      restartTerminalOnEdt(resolvedRegistry, startupLaunchSpec, replaceRetainedTerminal)
    }
  }

  internal fun flushPendingInitialMessageIfInitialized() {
    val initializedTab = tab ?: return
    initialMessageDispatcher?.schedule(initializedTab)
  }

  internal fun addPendingContextItems(items: List<AgentPromptContextItem>): Boolean {
    ensureInitialized()
    val panel = getOrCreatePendingContextPanel()
    val added = panel.addItems(items)
    val initializedTab = tab
    if (added && initializedTab != null) {
      ensurePendingContextPanelInstalled(panel)
      initializedTab.preferredFocusableComponent.requestFocusInWindow()
    }
    return added
  }

  internal fun pendingContextItemsForTests(): List<AgentPromptContextItem> = pendingContextPanel?.pendingItemsForTests().orEmpty()

  @TestOnly
  internal fun showComponentForTests() {
    component.showForTests()
  }

  private fun renderDeferredStartState(state: AgentThreadViewDeferredStartState) {
    val deferredContent = if (state.phase == AgentThreadViewDeferredStartPhase.WAITING) file.deferredStartContent() else null
    if (deferredContent == null) {
      file.clearDeferredStartContent()
    }
    disposeDeferredStartProgressTimers(component)
    component.removeAll()
    component.add(deferredContent?.component ?: createDeferredStartComponent(state), BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun restartTerminalOnEdt(
    liveTerminalRegistry: AgentThreadViewLiveTerminalRegistry,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
    replaceRetainedTerminal: Boolean,
  ): Boolean {
    if (disposed || tab == null) {
      return false
    }
    disposeTerminalAttachments(clearPendingContextPanel = false)
    val replaced = if (replaceRetainedTerminal) {
      liveTerminalRegistry.replace(
        file = file,
        terminalTabs = terminalTabs,
        startupLaunchSpec = startupLaunchSpec,
      )
      true
    }
    else {
      false
    }
    attachTerminalOnEdt(liveTerminalRegistry, startupLaunchSpec)
    return replaced
  }

  private fun disposeTerminalAttachments(clearPendingContextPanel: Boolean) {
    initialMessageDispatcher?.dispose()
    initialMessageDispatcher = null
    pendingThreadRefreshController?.dispose()
    pendingThreadRefreshController = null
    terminalTitleThreadRebindController?.dispose()
    terminalTitleThreadRebindController = null
    concreteThreadRebindController?.dispose()
    concreteThreadRebindController = null
    scopedTerminalRefreshController?.dispose()
    scopedTerminalRefreshController = null
    terminalRestoreContextController?.dispose()
    terminalRestoreContextController = null
    tab = null
    customContentPreferredFocusedComponent = null
    disposeDeferredStartProgressTimers(component)
    component.removeAll()
    pendingContextPanelInstalled = false
    if (clearPendingContextPanel) {
      pendingContextPanel = null
    }
  }

  private fun installPendingContextInterceptor(tab: AgentThreadViewTerminalTab) {
    tab.addInputInterceptor(this, TerminalInputInterceptor { event -> handlePendingContextInput(tab, event) })
  }

  private fun handlePendingContextInput(tab: AgentThreadViewTerminalTab, event: KeyEvent): Boolean {
    val panel = pendingContextPanel
    if (panel == null || !panel.hasItems() || !isPlainEnter(event)) {
      return false
    }

    val promptSuffix = resolvePendingContextPromptSuffix(panel) ?: return true
    when (tab.sendPendingContextAndExecute(promptSuffix)) {
      AgentThreadViewPendingContextSubmissionResult.SUBMITTED -> panel.clear()
      AgentThreadViewPendingContextSubmissionResult.UNAVAILABLE -> {
        StatusBar.Info.set(AgentThreadViewBundle.message("thread.view.pending.context.terminal.unavailable"), project)
      }
    }
    return true
  }

  private fun resolvePendingContextPromptSuffix(panel: AgentThreadViewPendingContextPanel): String? {
    val items = panel.pendingItemsSnapshot()
    if (items.isEmpty()) {
      return null
    }

    val softCapChars = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS
    val serializedChars = panel.measureContextBlockChars(items)
    if (serializedChars <= softCapChars) {
      return panel.buildPromptSuffix(
        items = items,
        summary = AgentPromptContextEnvelopeSummary(
          softCapChars = softCapChars,
          softCapExceeded = false,
          autoTrimApplied = false,
        ),
      )
    }

    val choice = Messages.showDialog(
      project,
      AgentThreadViewBundle.message("thread.view.pending.context.softcap.message", serializedChars, softCapChars),
      AgentThreadViewBundle.message("thread.view.pending.context.softcap.title"),
      arrayOf(
        AgentThreadViewBundle.message("thread.view.pending.context.softcap.action.send.full"),
        AgentThreadViewBundle.message("thread.view.pending.context.softcap.action.auto.trim"),
        CommonBundle.getCancelButtonText(),
      ),
      0,
      Messages.getWarningIcon(),
    )

    return when (choice) {
      0 -> panel.buildPromptSuffix(
        items = items,
        summary = AgentPromptContextEnvelopeSummary(
          softCapChars = softCapChars,
          softCapExceeded = true,
          autoTrimApplied = false,
        ),
      )
      1 -> {
        val trimResult = AgentPromptContextEnvelopeFormatter.applySoftCap(
          items = items,
          softCapChars = softCapChars,
          projectPath = file.projectPath,
        )
        panel.buildPromptSuffix(
          items = trimResult.items,
          summary = AgentPromptContextEnvelopeSummary(
            softCapChars = softCapChars,
            softCapExceeded = true,
            autoTrimApplied = true,
          ),
        )
      }
      else -> null
    }
  }

  private fun invalidateTabActionsCache() {
    cachedTabActionsProvider = null
    cachedTabActions = null
    cachedTabActionsInitialized = false
  }

  private fun createDeferredStartComponent(state: AgentThreadViewDeferredStartState): JComponent {
    val rootPanel = JPanel(GridBagLayout())
    val content = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = JBUI.Borders.empty(16)
      isOpaque = false
    }
    if (state.phase == AgentThreadViewDeferredStartPhase.WAITING) {
      content.add(createDelayedDeferredStartProgressIcon())
      content.add(Box.createVerticalStrut(JBUI.scale(8)))
    }
    content.add(createMessageArea(state.title).apply {
      alignmentX = Component.CENTER_ALIGNMENT
    })
    val stateMessage = state.message
    if (!stateMessage.isNullOrBlank()) {
      content.add(Box.createVerticalStrut(JBUI.scale(4)))
      content.add(createMessageArea(stateMessage, secondary = true).apply {
        alignmentX = Component.CENTER_ALIGNMENT
      })
    }
    rootPanel.accessibleContext.accessibleName = buildDeferredStartAccessibleName(state)
    rootPanel.add(content)
    return rootPanel
  }

  private fun createDelayedDeferredStartProgressIcon(): JComponent {
    val icon = createDeferredStartProgressComponent().apply {
      name = DEFERRED_START_PROGRESS_NAME
      isVisible = false
      suspendDeferredStartProgress()
    }
    val iconSize = icon.preferredSize
    val iconPanel = JPanel(BorderLayout()).apply {
      alignmentX = Component.CENTER_ALIGNMENT
      isOpaque = false
      preferredSize = iconSize
      minimumSize = iconSize
      maximumSize = Dimension(iconSize.width, iconSize.height)
      add(icon, BorderLayout.CENTER)
    }
    val timer = Timer(DEFERRED_START_PROGRESS_DELAY_MS) {
      iconPanel.putClientProperty(DEFERRED_START_PROGRESS_TIMER_PROPERTY, null)
      if (iconPanel.parent != null) {
        icon.isVisible = true
        icon.resumeDeferredStartProgress()
        iconPanel.revalidate()
        iconPanel.repaint()
      }
    }.apply {
      isRepeats = false
      start()
    }
    iconPanel.putClientProperty(DEFERRED_START_PROGRESS_TIMER_PROPERTY, timer)
    return iconPanel
  }
}

private fun disposeDeferredStartProgressTimers(component: Component) {
  if (component is JComponent) {
    (component.getClientProperty(DEFERRED_START_PROGRESS_TIMER_PROPERTY) as? Timer)?.stop()
    component.putClientProperty(DEFERRED_START_PROGRESS_TIMER_PROPERTY, null)
  }
  if (component is Container) {
    component.components.forEach(::disposeDeferredStartProgressTimers)
  }
}

private fun createDeferredStartProgressComponent(): JComponent {
  if (ApplicationManager.getApplication() == null) {
    return JProgressBar().apply {
      isIndeterminate = true
      isBorderPainted = false
    }
  }
  return AsyncProcessIcon(DEFERRED_START_PROGRESS_NAME)
}

private fun JComponent.suspendDeferredStartProgress() {
  (this as? AsyncProcessIcon)?.suspend()
}

private fun JComponent.resumeDeferredStartProgress() {
  (this as? AsyncProcessIcon)?.resume()
}

private fun buildDeferredStartAccessibleName(state: AgentThreadViewDeferredStartState): @Nls String {
  val stateMessage = state.message?.takeIf { it.isNotBlank() } ?: return state.title
  return "${state.title}. $stateMessage"
}

private fun createMessageArea(text: @Nls String, secondary: Boolean = false): JComponent {
  return JTextArea(text).apply {
    isEditable = false
    isFocusable = false
    lineWrap = true
    wrapStyleWord = true
    isOpaque = false
    border = null
    if (secondary) {
      foreground = UIUtil.getContextHelpForeground()
    }
  }
}


private fun isPlainEnter(event: KeyEvent): Boolean {
  return event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ENTER && event.modifiersEx == 0
}

private fun shouldBlockTerminalInitialization(state: AgentThreadViewDeferredStartState?): Boolean {
  return when (state?.phase) {
    AgentThreadViewDeferredStartPhase.WAITING,
    AgentThreadViewDeferredStartPhase.SUCCESS_NO_START,
    AgentThreadViewDeferredStartPhase.FAILURE_NO_START,
      -> true

    else -> false
  }
}

private class AgentThreadViewFileEditorComponent : JPanel(BorderLayout()) {
  private var showingForTests: Boolean = false
  private var showingContinuation: CancellableContinuation<Unit>? = null
  private var showingListener: HierarchyListener? = null

  init {
    isFocusable = true
  }

  suspend fun awaitShowing() {
    if (isShowing || showingForTests) {
      return
    }
    suspendCancellableCoroutine { continuation ->
      showingContinuation?.cancel(CancellationException("Superseded by a new Agent Thread View editor showing waiter"))
      removeShowingListener()
      showingContinuation = continuation
      val listener = HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && isShowing) {
          resumeShowingContinuation()
        }
      }
      showingListener = listener
      addHierarchyListener(listener)
      continuation.invokeOnCancellation {
        if (showingContinuation === continuation) {
          showingContinuation = null
          removeShowingListener()
        }
      }
      if (isShowing || showingForTests) {
        resumeShowingContinuation()
      }
    }
  }

  fun cancelAwaitShowing() {
    showingContinuation?.cancel(CancellationException("Agent Thread View editor disposed before it was shown"))
    showingContinuation = null
    removeShowingListener()
  }

  @TestOnly
  fun showForTests() {
    showingForTests = true
    resumeShowingContinuation()
  }

  private fun resumeShowingContinuation() {
    val continuation = showingContinuation ?: return
    showingContinuation = null
    removeShowingListener()
    if (continuation.isActive) {
      continuation.resume(Unit)
    }
  }

  private fun removeShowingListener() {
    showingListener?.let(::removeHierarchyListener)
    showingListener = null
  }

}

internal fun interface AgentThreadViewTabSnapshotWriter {
  suspend fun upsert(snapshot: AgentThreadViewTabSnapshot)
}

private object ApplicationAgentThreadViewTabSnapshotWriter : AgentThreadViewTabSnapshotWriter {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun upsert(snapshot: AgentThreadViewTabSnapshot) = Unit
}

internal fun interface AgentThreadViewArchivedRestoreHandler {
  suspend fun closeAndForget(file: AgentThreadViewVirtualFile)
}

private object ApplicationAgentThreadViewArchivedRestoreHandler : AgentThreadViewArchivedRestoreHandler {
  override suspend fun closeAndForget(file: AgentThreadViewVirtualFile) {
    closeAndForgetAgentThreadViewsForThread(
      projectPath = file.projectPath,
      threadIdentity = file.threadIdentity,
      subAgentId = file.subAgentId,
    )
  }
}

internal fun AgentSessionThread.matchesRestoredAgentThreadViewFile(file: AgentThreadViewVirtualFile): Boolean {
  if (provider != file.provider) {
    return false
  }
  if (id == file.sessionId || id == file.threadId) {
    return true
  }
  val restoredSubAgentId = file.subAgentId ?: return false
  return subAgents.any { subAgent -> subAgent.id == restoredSubAgentId || subAgent.id == file.threadId }
}

internal fun buildAgentThreadViewEditorTabActionGroup(actions: List<AnAction>): ActionGroup? {
  if (actions.isEmpty()) {
    return null
  }
  if (actions.size == 1) {
    val singleAction = actions.single()
    return singleAction as? ActionGroup ?: DumbAwareAgentThreadViewActionGroup(singleAction)
  }
  return DumbAwareAgentThreadViewActionGroup(actions)
}

private class DumbAwareAgentThreadViewActionGroup : DefaultActionGroup, DumbAware {
  constructor(vararg actions: AnAction) : super(*actions)

  constructor(actions: List<AnAction>) : super(actions)
}
