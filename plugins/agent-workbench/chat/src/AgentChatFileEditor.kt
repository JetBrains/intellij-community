// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.CommonBundle
import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchContributors
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecs
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.ide.OccurenceNavigator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val terminalTabs: AgentChatTerminalTabs = ToolWindowAgentChatTerminalTabs,
  private val liveTerminalRegistry: AgentChatLiveTerminalRegistry? = null,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter = ApplicationAgentChatTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
  editorCoroutineScope: CoroutineScope? = null,
) : UserDataHolderBase(), FileEditor {
  private val ownedTerminalStartupJob = if (editorCoroutineScope == null) SupervisorJob() else null

  @Suppress("RAW_SCOPE_CREATION")
  private val terminalStartupScope = editorCoroutineScope ?: CoroutineScope(checkNotNull(ownedTerminalStartupJob) + Dispatchers.Default)
  private val pendingContextPanel by lazy { AgentChatPendingContextPanel(file.projectPath) }
  private val component = AgentChatFileEditorComponent {
    semanticRegionController?.occurrenceNavigator() ?: OccurenceNavigator.EMPTY
  }

  private fun buildEditorTabActions(): ActionGroup? {
    val actionManager = ActionManager.getInstance()
    val providerActionIds = providerDescriptor?.editorTabActionIds.orEmpty()
    val actions = buildList {
      listOf(
        PREVIOUS_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID,
        NEXT_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID,
      ).forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
      providerActionIds.forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
    }
    return buildAgentChatEditorTabActionGroup(actions)
  }

  private var tab: AgentChatTerminalTab? = null
  private var initializationStarted: Boolean = false
  private var initializationRequested: Boolean = false
  private var stateApplied: Boolean = file.projectPath.isNotBlank() || file.threadIdentity.isNotBlank()
  private var initializationJob: Job? = null
  private var disposed: Boolean = false
  private var pendingThreadRefreshController: AgentChatPendingThreadRefreshController? = null
  private var codexTerminalTitleThreadRebindController: AgentChatDisposableController? = null
  private var concreteThreadRebindController: AgentChatConcreteThreadRebindController? = null
  private var initialMessageDispatcher: AgentChatInitialMessageDispatcher? = null
  private var scopedTerminalRefreshController: AgentChatDisposableController? = null
  private var terminalRestoreContextController: AgentChatDisposableController? = null
  private var patchFoldController: AgentChatDisposableController? = null
  private var semanticRegionController: AgentChatSemanticRegionController? = null
  private var crossProjectDockTargetRegistration: Disposable? = null

  private val providerDescriptor
    get() = file.provider?.let(AgentSessionProviders::find)

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    return tab?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = AgentChatBundle.message("chat.filetype.name")

  override fun getTabActions(): ActionGroup? = buildEditorTabActions()

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (!file.shouldRestoreOnRestart() || file.projectPath.isBlank() || file.threadIdentity.isBlank()) {
      return AgentChatFileEditorState(snapshot = null)
    }
    return AgentChatFileEditorState(snapshot = file.toSnapshot(), startupIntent = file.startupIntent())
  }

  override fun setState(state: FileEditorState) {
    val chatState = state as? AgentChatFileEditorState ?: return
    stateApplied = true
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
    ensureInitialized()
  }

  override fun dispose() {
    disposed = true
    initializationJob?.cancel()
    initializationJob = null
    ownedTerminalStartupJob?.cancel()
    crossProjectDockTargetRegistration?.let(Disposer::dispose)
    crossProjectDockTargetRegistration = null
    initialMessageDispatcher?.dispose()
    initialMessageDispatcher = null
    pendingThreadRefreshController?.dispose()
    pendingThreadRefreshController = null
    codexTerminalTitleThreadRebindController?.dispose()
    codexTerminalTitleThreadRebindController = null
    concreteThreadRebindController?.dispose()
    concreteThreadRebindController = null
    scopedTerminalRefreshController?.dispose()
    scopedTerminalRefreshController = null
    terminalRestoreContextController?.dispose()
    terminalRestoreContextController = null
    patchFoldController?.dispose()
    patchFoldController = null
    semanticRegionController?.dispose()
    semanticRegionController = null
    tab = null
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
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(startupIntent.launchMode)
    val augmented = AgentSessionLaunchSpecs.augment(
      projectPath = file.projectPath,
      provider = startupIntent.provider,
      launchSpec = baseLaunchSpec,
    )
    return AgentSessionLaunchContributors.applyAll(
      projectPath = file.projectPath,
      provider = startupIntent.provider,
      sessionId = null,
      launchSpec = augmented,
    )
  }

  private suspend fun resolveResumeLaunchSpec(): AgentSessionTerminalLaunchSpec {
    val provider = file.provider ?: throw IllegalStateException("Missing Agent Chat provider for ${file.url}")
    return AgentSessionLaunchSpecs.resolveResume(
      projectPath = file.projectPath,
      provider = provider,
      sessionId = file.threadId.ifBlank { file.sessionId },
      launchMode = parseAgentChatLaunchMode(file.launchMode),
    )
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
    val behavior = resolveAgentChatProviderBehavior(file.provider)
    val createdTab = liveTerminalRegistry.acquireOrCreate(
      file = file,
      terminalTabs = terminalTabs,
      startupLaunchSpec = startupLaunchSpec,
    )
    tab = createdTab
    file.updateStartupIntent(null)
    if (suppressInitialMessageDispatch) {
      // The startup command already carried this prompt; do not snapshot and replay the fallback after title rebind
      // or restore.
      file.clearInitialMessageDispatchMetadata()
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
    codexTerminalTitleThreadRebindController = createCodexTerminalTitleThreadRebindController(
      file = file,
      tab = createdTab,
      tabSnapshotWriter = tabSnapshotWriter,
    )
    patchFoldController = behavior.createPatchFoldController(createdTab)
    semanticRegionController = behavior.createSemanticRegionController(createdTab)
    installPendingContextInterceptor(createdTab)
    component.removeAll()
    component.add(createdTab.component, BorderLayout.CENTER)
    component.add(pendingContextPanel.component, BorderLayout.SOUTH)
    installAgentChatTerminalFileDropSupport(createdTab.component, createdTab, this)
    installAgentChatContextFileDropSupport(pendingContextPanel.component, ::addPendingContextItems, this)
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

  internal fun flushPendingInitialMessageIfInitialized() {
    val initializedTab = tab ?: return
    initialMessageDispatcher?.schedule(initializedTab)
  }

  internal fun addPendingContextItems(items: List<AgentPromptContextItem>): Boolean {
    ensureInitialized()
    val added = pendingContextPanel.addItems(items)
    tab?.preferredFocusableComponent?.requestFocusInWindow()
    return added
  }

  internal fun pendingContextItemsForTests(): List<AgentPromptContextItem> = pendingContextPanel.pendingItemsForTests()

  internal fun canNavigateProposedPlan(direction: AgentChatSemanticNavigationDirection): Boolean {
    return semanticRegionController?.canNavigate(direction) == true
  }

  internal fun navigateProposedPlan(direction: AgentChatSemanticNavigationDirection): Boolean {
    return semanticRegionController?.navigate(direction) == true
  }

  private fun renderDeferredStartState(state: AgentChatDeferredStartState) {
    component.removeAll()
    component.add(createDeferredStartComponent(state), BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun installPendingContextInterceptor(tab: AgentChatTerminalTab) {
    tab.addInputInterceptor(this, TerminalInputInterceptor { event -> handlePendingContextInput(tab, event) })
  }

  private fun handlePendingContextInput(tab: AgentChatTerminalTab, event: KeyEvent): Boolean {
    if (!pendingContextPanel.hasItems() || !isPlainEnter(event)) {
      return false
    }

    val promptSuffix = resolvePendingContextPromptSuffix() ?: return true
    when (tab.sendPendingContextAndExecute(promptSuffix)) {
      AgentChatPendingContextSubmissionResult.SUBMITTED -> pendingContextPanel.clear()
      AgentChatPendingContextSubmissionResult.UNAVAILABLE -> {
        StatusBar.Info.set(AgentChatBundle.message("chat.pending.context.terminal.unavailable"), project)
      }
    }
    return true
  }

  private fun resolvePendingContextPromptSuffix(): String? {
    val items = pendingContextPanel.pendingItemsSnapshot()
    if (items.isEmpty()) {
      return null
    }

    val softCapChars = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS
    val serializedChars = pendingContextPanel.measureContextBlockChars(items)
    if (serializedChars <= softCapChars) {
      return pendingContextPanel.buildPromptSuffix(
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
      0 -> pendingContextPanel.buildPromptSuffix(
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
        pendingContextPanel.buildPromptSuffix(
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

private class AgentChatFileEditorComponent(
  private val navigatorProvider: () -> OccurenceNavigator,
) : JPanel(BorderLayout()), OccurenceNavigator {
  override fun hasNextOccurence(): Boolean = navigatorProvider().hasNextOccurence()

  override fun hasPreviousOccurence(): Boolean = navigatorProvider().hasPreviousOccurence()

  override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? = navigatorProvider().goNextOccurence()

  override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? = navigatorProvider().goPreviousOccurence()

  override fun getNextOccurenceActionName(): String = navigatorProvider().nextOccurenceActionName

  override fun getPreviousOccurenceActionName(): String = navigatorProvider().previousOccurenceActionName

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal fun interface AgentChatTabSnapshotWriter {
  suspend fun upsert(snapshot: AgentChatTabSnapshot)
}

private object ApplicationAgentChatTabSnapshotWriter : AgentChatTabSnapshotWriter {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun upsert(snapshot: AgentChatTabSnapshot) = Unit
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

private const val PREVIOUS_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID: String =
  AgentWorkbenchActionIds.Sessions.EditorTab.PREVIOUS_PROPOSED_PLAN
private const val NEXT_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEXT_PROPOSED_PLAN
