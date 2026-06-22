// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-editor.spec.md

import com.intellij.CommonBundle
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialPromptDeliveryChannel
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
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
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.coroutines.resume

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val terminalTabs: AgentChatTerminalTabs = ToolWindowAgentChatTerminalTabs,
  private val liveTerminalRegistry: AgentChatLiveTerminalRegistry? = null,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter = ApplicationAgentChatTabSnapshotWriter,
  private val archivedRestoreHandler: AgentChatArchivedRestoreHandler = ApplicationAgentChatArchivedRestoreHandler,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
  editorCoroutineScope: CoroutineScope? = null,
  private val providerDescriptorResolver: (AgentSessionProvider) -> AgentSessionProviderDescriptor? = AgentSessionProviders::find,
  private val behaviorResolver: (AgentSessionProvider?) -> AgentChatProviderBehavior = ::resolveAgentChatProviderBehavior,
) : UserDataHolderBase(), FileEditor {
  private val ownedTerminalStartupJob = if (editorCoroutineScope == null) SupervisorJob() else null

  @Suppress("RAW_SCOPE_CREATION")
  private val terminalStartupScope = editorCoroutineScope ?: CoroutineScope(checkNotNull(ownedTerminalStartupJob) + Dispatchers.Default)
  private val component = AgentChatFileEditorComponent()

  private fun buildEditorTabActions(): ActionGroup? {
    val actionManager = ActionManager.getInstance()
    val providerActionIds = providerDescriptor?.editorTabActionIds.orEmpty()
    val actions = buildList {
      providerActionIds.forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
    }
    return buildAgentChatEditorTabActionGroup(actions)
  }

  private var cachedTabActionsProvider: AgentSessionProvider? = null
  private var cachedTabActions: ActionGroup? = null
  private var cachedTabActionsInitialized: Boolean = false
  private var tab: AgentChatTerminalTab? = null
  private var pendingContextPanel: AgentChatPendingContextPanel? = null
  private var pendingContextPanelInstalled: Boolean = false
  private var initializationStarted: Boolean = false
  private var initializationRequested: Boolean = false
  private var focusTerminalAfterInitialization: Boolean = false
  private var stateApplied: Boolean = file.projectPath.isNotBlank() || file.threadIdentity.isNotBlank()
  private var initializationJob: Job? = null
  private var disposed: Boolean = false
  private var pendingThreadRefreshController: AgentChatPendingThreadRefreshController? = null
  private var terminalTitleThreadRebindController: AgentChatDisposableController? = null
  private var concreteThreadRebindController: AgentChatConcreteThreadRebindController? = null
  private var initialMessageDispatcher: AgentChatInitialMessageDispatcher? = null
  private var scopedTerminalRefreshController: AgentChatDisposableController? = null
  private var terminalRestoreContextController: AgentChatDisposableController? = null
  private var crossProjectDockTargetRegistration: Disposable? = null

  private val providerDescriptor
    get() = file.provider?.let(providerDescriptorResolver)

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    return tab?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = AgentChatBundle.message("chat.filetype.name")

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
      return AgentChatFileEditorState(snapshot = null)
    }
    return AgentChatFileEditorState(snapshot = file.toSnapshot(), startupIntent = file.startupIntent())
  }

  override fun setState(state: FileEditorState) {
    val chatState = state as? AgentChatFileEditorState ?: return
    stateApplied = true
    val providerBeforeUpdate = file.provider
    val snapshot = chatState.snapshot
    if (snapshot != null) {
      file.updateRestoreOnRestart(true)
      file.updateFromResolution(AgentChatTabResolution.Resolved(snapshot))
      file.updateStartupIntent(chatState.startupIntent)
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

  override fun getFile(): AgentChatVirtualFile = file

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
    if (initializationStarted) {
      return
    }
    val validationError = validateAgentChatFile(file)
    if (validationError != null) {
      handleRestoreValidationError(validationError)
      return
    }
    initializationStarted = true
    val startupLaunchSpecOverride = file.consumeStartupLaunchSpecOverride()
    val suppressInitialMessageDispatch = startupLaunchSpecOverride != null && file.consumeSuppressInitialMessageDispatchOnStartup()
    val startupIntent = file.startupIntent()
    if (startupLaunchSpecOverride == null && file.isPendingThread && startupIntent == null) {
      handleRestoreValidationError(AgentChatBundle.message("chat.restore.validation.pending.thread"))
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
        AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, e)
      }
    }
  }

  private suspend fun isRestoredArchivedThread(descriptor: AgentSessionProviderDescriptor?): Boolean {
    val source = descriptor?.sessionSource ?: return false
    if (!source.supportsArchivedThreads) {
      return false
    }
    val archivedThreads = try {
      source.listArchivedThreadsFromOpenProject(path = file.projectPath, project = project)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: Throwable) {
      return false
    }
    return archivedThreads.any { thread -> thread.matchesRestoredAgentChatFile(file) }
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

  private suspend fun resolveLiveTerminalRegistry(): AgentChatLiveTerminalRegistry {
    return liveTerminalRegistry ?: project.serviceAsync<AgentChatLiveTerminalRegistryService>()
  }

  private suspend fun resolveStartupLaunchSpec(startupIntent: AgentChatStartupIntent?): AgentSessionTerminalLaunchSpec {
    return when (startupIntent) {
      is AgentChatStartupIntent.NewSession -> resolveNewSessionLaunchSpec(startupIntent)
      null -> resolveResumeLaunchSpec()
    }
  }

  private suspend fun resolveNewSessionLaunchSpec(startupIntent: AgentChatStartupIntent.NewSession): AgentSessionTerminalLaunchSpec {
    val descriptor = AgentSessionProviders.find(startupIntent.provider)
                     ?: throw IllegalStateException("Missing Agent Chat provider for ${startupIntent.provider.value}")
    if (startupIntent.launchMode !in descriptor.supportedLaunchModes) {
      throw IllegalStateException("Unsupported Agent Chat launch mode ${startupIntent.launchMode} for ${startupIntent.provider.value}")
    }
    return AgentSessionLaunchPlanner.plan(
      intent = AgentSessionLaunchIntent(
        projectPath = file.projectPath,
        provider = startupIntent.provider,
        operation = AgentSessionLaunchOperation.NEW,
        launchMode = startupIntent.launchMode,
        generationSettings = file.generationSettings,
      ),
      project = project,
    ).launchSpec
  }

  private suspend fun resolveResumeLaunchSpec(): AgentSessionTerminalLaunchSpec {
    val provider = file.provider ?: throw IllegalStateException("Missing Agent Chat provider for ${file.url}")
    return AgentSessionLaunchPlanner.plan(
      intent = AgentSessionLaunchIntent(
        projectPath = file.projectPath,
        provider = provider,
        operation = AgentSessionLaunchOperation.RESUME,
        sessionId = file.threadId.ifBlank { file.sessionId },
        launchMode = parseAgentChatLaunchMode(file.launchMode),
        generationSettings = file.generationSettings,
      ),
      project = project,
    ).launchSpec
  }

  private suspend fun attachTerminal(
    liveTerminalRegistry: AgentChatLiveTerminalRegistry,
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
    liveTerminalRegistry: AgentChatLiveTerminalRegistry,
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
    suppressInitialMessageDispatch: Boolean = false,
  ) {
    if (disposed || tab != null) {
      return
    }
    ensureCrossProjectDockTargetRegistration()
    val deferredStartState = file.deferredStartState
    if (deferredStartState?.phase == AgentChatDeferredStartPhase.READY_TO_START) {
      file.updateDeferredStartState(null)
    }
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
    val pendingController = AgentChatPendingThreadRefreshController(
      file = file,
      behavior = behavior,
      tabSnapshotWriter = tabSnapshotWriter,
      currentTimeProvider = currentTimeProvider,
      retryIntervalMs = pendingScopedRefreshRetryIntervalMs,
    )
    pendingThreadRefreshController = pendingController
    val concreteController = AgentChatConcreteThreadRebindController(
      file = file,
      behavior = behavior,
      tabSnapshotWriter = tabSnapshotWriter,
      currentTimeProvider = currentTimeProvider,
    )
    concreteThreadRebindController = concreteController
    val messageDispatcher = AgentChatInitialMessageDispatcher(
      project = project,
      file = file,
      behavior = behavior,
      tabSnapshotWriter = tabSnapshotWriter,
    )
    initialMessageDispatcher = messageDispatcher
    pendingController.attach(createdTab)
    concreteController.attach(createdTab, providerDescriptor)
    if (!suppressInitialMessageDispatch) {
      messageDispatcher.schedule(createdTab)
    }
    scopedTerminalRefreshController = createAgentChatScopedTerminalRefreshController(file, createdTab, providerDescriptor)
    val restoreContextController = AgentChatTerminalRestoreContextController(
      file = file,
      descriptor = providerDescriptor,
      parentDisposable = this,
    )
    terminalRestoreContextController = restoreContextController
    restoreContextController.attach(createdTab)
    terminalTitleThreadRebindController = createAgentChatTerminalTitleThreadRebindController(
      file = file,
      tab = createdTab,
      tabSnapshotWriter = tabSnapshotWriter,
    )
    installPendingContextInterceptor(createdTab)
    component.removeAll()
    pendingContextPanelInstalled = false
    component.add(createdTab.component, BorderLayout.CENTER)
    installAgentChatTerminalFileDropSupport(createdTab.component, createdTab, this)
    pendingContextPanel?.let(::ensurePendingContextPanelInstalled)
    component.revalidate()
    component.repaint()
    focusTerminalIfRequested(createdTab)
  }

  private fun focusTerminalIfRequested(createdTab: AgentChatTerminalTab) {
    if (!focusTerminalAfterInitialization) {
      return
    }
    focusTerminalAfterInitialization = false
    createdTab.preferredFocusableComponent.requestFocusInWindow()
  }

  private fun getOrCreatePendingContextPanel(): AgentChatPendingContextPanel {
    pendingContextPanel?.let {
      return it
    }
    return AgentChatPendingContextPanel(file.projectPath).also { panel ->
      pendingContextPanel = panel
    }
  }

  private fun ensurePendingContextPanelInstalled(panel: AgentChatPendingContextPanel) {
    if (pendingContextPanelInstalled) {
      return
    }
    component.add(panel.component, BorderLayout.SOUTH)
    installAgentChatContextFileDropSupport(panel.component, ::addPendingContextItems, this)
    pendingContextPanelInstalled = true
    component.revalidate()
    component.repaint()
  }

  private fun ensureCrossProjectDockTargetRegistration() {
    if (crossProjectDockTargetRegistration == null && file.projectPath.isNotBlank()) {
      crossProjectDockTargetRegistration = AgentChatCrossProjectDockTargetRegistrar().register(project, file)
    }
  }

  private fun handleRestoreValidationError(validationError: String) {
    if (file.projectPath.isBlank() && file.threadIdentity.isBlank()) {
      if (!project.isDisposed) {
        FileEditorManager.getInstance(project).closeFile(file)
      }
      return
    }
    forgetAgentChatTabMetadata(file.tabKey)
    AgentChatRestoreNotificationService.reportRestoreFailure(project, file, validationError)
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

  private fun renderDeferredStartState(state: AgentChatDeferredStartState) {
    component.removeAll()
    component.add(createDeferredStartComponent(state), BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun restartTerminalOnEdt(
    liveTerminalRegistry: AgentChatLiveTerminalRegistry,
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
    component.removeAll()
    pendingContextPanelInstalled = false
    if (clearPendingContextPanel) {
      pendingContextPanel = null
    }
  }

  private fun installPendingContextInterceptor(tab: AgentChatTerminalTab) {
    tab.addInputInterceptor(this, TerminalInputInterceptor { event -> handlePendingContextInput(tab, event) })
  }

  private fun handlePendingContextInput(tab: AgentChatTerminalTab, event: KeyEvent): Boolean {
    val panel = pendingContextPanel
    if (panel == null || !panel.hasItems() || !isPlainEnter(event)) {
      return false
    }

    val promptSuffix = resolvePendingContextPromptSuffix(panel) ?: return true
    when (tab.sendPendingContextAndExecute(promptSuffix)) {
      AgentChatPendingContextSubmissionResult.SUBMITTED -> panel.clear()
      AgentChatPendingContextSubmissionResult.UNAVAILABLE -> {
        StatusBar.Info.set(AgentChatBundle.message("chat.pending.context.terminal.unavailable"), project)
      }
    }
    return true
  }

  private fun resolvePendingContextPromptSuffix(panel: AgentChatPendingContextPanel): String? {
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
      AgentChatBundle.message("chat.pending.context.softcap.message", serializedChars, softCapChars),
      AgentChatBundle.message("chat.pending.context.softcap.title"),
      arrayOf(
        AgentChatBundle.message("chat.pending.context.softcap.action.send.full"),
        AgentChatBundle.message("chat.pending.context.softcap.action.auto.trim"),
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

  private fun createDeferredStartComponent(state: AgentChatDeferredStartState): JComponent {
    val content = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
    }
    content.add(createMessageArea(state.title, bold = true))
    val stateMessage = state.message
    if (!stateMessage.isNullOrBlank()) {
      content.add(Box.createVerticalStrut(8))
      content.add(createMessageArea(stateMessage, bold = false))
    }
    return JPanel(BorderLayout()).apply {
      add(content, BorderLayout.NORTH)
    }
  }
}

private fun createMessageArea(text: @Nls String, bold: Boolean): JComponent {
  return JTextArea(text).apply {
    isEditable = false
    isFocusable = false
    lineWrap = true
    wrapStyleWord = true
    isOpaque = false
    border = null
    font = if (bold) font.deriveFont(font.style or java.awt.Font.BOLD) else font
  }
}


private fun isPlainEnter(event: KeyEvent): Boolean {
  return event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ENTER && event.modifiersEx == 0
}

private fun shouldBlockTerminalInitialization(state: AgentChatDeferredStartState?): Boolean {
  return when (state?.phase) {
    AgentChatDeferredStartPhase.WAITING,
    AgentChatDeferredStartPhase.SUCCESS_NO_START,
    AgentChatDeferredStartPhase.FAILURE_NO_START,
      -> true

    else -> false
  }
}

private class AgentChatFileEditorComponent : JPanel(BorderLayout()) {
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
      showingContinuation?.cancel(CancellationException("Superseded by a new Agent Chat editor showing waiter"))
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
    showingContinuation?.cancel(CancellationException("Agent Chat editor disposed before it was shown"))
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

internal fun interface AgentChatTabSnapshotWriter {
  suspend fun upsert(snapshot: AgentChatTabSnapshot)
}

private object ApplicationAgentChatTabSnapshotWriter : AgentChatTabSnapshotWriter {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun upsert(snapshot: AgentChatTabSnapshot) = Unit
}

internal fun interface AgentChatArchivedRestoreHandler {
  suspend fun closeAndForget(file: AgentChatVirtualFile)
}

private object ApplicationAgentChatArchivedRestoreHandler : AgentChatArchivedRestoreHandler {
  override suspend fun closeAndForget(file: AgentChatVirtualFile) {
    closeAndForgetAgentChatsForThread(
      projectPath = file.projectPath,
      threadIdentity = file.threadIdentity,
      subAgentId = file.subAgentId,
    )
  }
}

internal fun AgentSessionThread.matchesRestoredAgentChatFile(file: AgentChatVirtualFile): Boolean {
  if (provider != file.provider) {
    return false
  }
  if (id == file.sessionId || id == file.threadId) {
    return true
  }
  val restoredSubAgentId = file.subAgentId ?: return false
  return subAgents.any { subAgent -> subAgent.id == restoredSubAgentId || subAgent.id == file.threadId }
}

internal fun buildAgentChatEditorTabActionGroup(actions: List<AnAction>): ActionGroup? {
  if (actions.isEmpty()) {
    return null
  }
  if (actions.size == 1) {
    val singleAction = actions.single()
    return singleAction as? ActionGroup ?: DumbAwareAgentChatActionGroup(singleAction)
  }
  return DumbAwareAgentChatActionGroup(actions)
}

private class DumbAwareAgentChatActionGroup : DefaultActionGroup, DumbAware {
  constructor(vararg actions: AnAction) : super(*actions)

  constructor(actions: List<AnAction>) : super(actions)
}
