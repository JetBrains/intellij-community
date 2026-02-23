// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

private const val NEW_THREAD_FROM_EDITOR_TAB_ACTION_ID = "AgentWorkbenchChat.NewThreadFromEditorTab"

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val terminalTabs: AgentChatTerminalTabs = ToolWindowAgentChatTerminalTabs,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter = ApplicationAgentChatTabSnapshotWriter,
) : UserDataHolderBase(), FileEditor {
  private val component = JPanel(BorderLayout())
  private val editorTabActions: ActionGroup? by lazy {
    val action = ActionManager.getInstance().getAction(NEW_THREAD_FROM_EDITOR_TAB_ACTION_ID) ?: return@lazy null
    action as? ActionGroup ?: DefaultActionGroup(action)
  }
  private var tab: TerminalToolWindowTab? = null
  private var initializationStarted: Boolean = false
  private var disposed: Boolean = false
  private var pendingInitialMessageJob: Job? = null
  private var codexTuiPatchFoldController: CodexTuiPatchFoldController? = null

  private val providerBehavior
    get() = file.provider?.let(AgentSessionProviderBehaviors::find)

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    return tab?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = file.threadTitle

  override fun getTabActions(): ActionGroup? = editorTabActions

  override fun setState(state: FileEditorState) = Unit

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
    tab?.view?.coroutineScope?.cancel()
    tab = null
    component.removeAll()
  }

  private fun ensureInitialized() {
    if (initializationStarted || disposed) {
      return
    }
    initializationStarted = true
    try {
      val terminalManager = TerminalToolWindowTabsManager.getInstance(project)
      val createdTab = terminalManager.createTabBuilder()
        .shouldAddToToolWindow(false)
        .workingDirectory(file.projectPath)
        .tabName(file.threadTitle)
        .shellCommand(file.shellCommand)
        .createTab()
      tab = createdTab
      subscribePendingFirstInput(createdTab)
      subscribeConcreteCodexNewThreadRebind(createdTab)
      scheduleInitialMessageSend(createdTab)
      codexTuiPatchFoldController = createdTab.terminalView
        ?.takeIf { shouldInstallCodexTuiPatchFolding(file.provider) }
        ?.let { terminalView ->
          CodexTuiPatchFoldController(
            terminalView = terminalView,
            sessionState = createdTab.sessionState,
            parentScope = createdTab.coroutineScope,
          )
        }
      component.removeAll()
      component.add(createdTab.component, BorderLayout.CENTER)
      component.revalidate()
      component.repaint()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, e)
    }
  }

  private fun subscribeConcreteCodexNewThreadRebind(createdTab: AgentChatTerminalTab) {
    val provider = file.provider
    if (provider == null || providerBehavior?.supportsNewThreadRebind != true || file.isPendingThread || file.subAgentId != null) {
      return
    }
    createdTab.coroutineScope.launch {
      val commandTracker = AgentChatTerminalCommandTracker()
      createdTab.keyEventsFlow.collectLatest { event ->
        val executedCommand = commandTracker.record(event.awtEvent) ?: return@collectLatest
        if (executedCommand != "/new") {
          return@collectLatest
        }
        if (!file.updateNewThreadRebindRequestedAtMs(System.currentTimeMillis())) {
          return@collectLatest
        }
        tabSnapshotWriter.upsert(file.toSnapshot())
        notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = file.projectPath)
      }
    }
  }

  internal fun flushPendingInitialMessageIfInitialized() {
    val initializedTab = tab ?: return
    scheduleInitialMessageSend(initializedTab)
  }

  private fun subscribePendingFirstInput(createdTab: AgentChatTerminalTab) {
    if (!file.isPendingThread || providerBehavior?.supportsPendingEditorTabRebind != true) {
      return
    }
    createdTab.coroutineScope.launch {
      createdTab.keyEventsFlow.collectLatest {
        if (!file.markPendingFirstInputAtMsIfAbsent(System.currentTimeMillis())) {
          return@collectLatest
        }
        tabSnapshotWriter.upsert(file.toSnapshot())
      }
    }
  }

  private fun scheduleInitialMessageSend(createdTab: AgentChatTerminalTab) {
    if (!file.hasPendingInitialMessageForDispatch()) {
      return
    }
    if (createdTab.sessionState.value == TerminalViewSessionState.Terminated) {
      return
    }
    if (pendingInitialMessageJob?.isActive == true) {
      return
    }
    pendingInitialMessageJob = createdTab.coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val state = createdTab.sessionState.first { it != TerminalViewSessionState.NotStarted }
      if (state != TerminalViewSessionState.Running) {
        return@launch
      }
      while (true) {
        when (createdTab.awaitInitialMessageReadiness(
          timeoutMs = INITIAL_MESSAGE_READINESS_TIMEOUT_MS,
          idleMs = INITIAL_MESSAGE_OUTPUT_IDLE_MS,
        )) {
          AgentChatTerminalInputReadiness.READY -> {
            sendInitialMessageIfReady(createdTab)
            return@launch
          }
          AgentChatTerminalInputReadiness.TIMEOUT -> {
            if (file.shouldDelayInitialMessageOnReadinessTimeout()) {
              yield()
              continue
            }
            sendInitialMessageIfReady(createdTab)
            return@launch
          }
          AgentChatTerminalInputReadiness.TERMINATED -> return@launch
        }
      }
    }.also { job ->
      job.invokeOnCompletion {
        if (pendingInitialMessageJob === job) {
          pendingInitialMessageJob = null
        }
      }
    }
  }

  private suspend fun sendInitialMessageIfReady(createdTab: AgentChatTerminalTab): Boolean {
    if (createdTab.sessionState.value != TerminalViewSessionState.Running) {
      return false
    }
    val dispatch = file.acquireInitialMessageDispatch() ?: return false
    try {
      createdTab.sendText(dispatch.message, shouldExecute = true)
    }
    catch (e: CancellationException) {
      file.cancelInitialMessageDispatch(dispatch)
      throw e
    }
    catch (_: Throwable) {
      file.cancelInitialMessageDispatch(dispatch)
      return false
    }
    if (!file.completeInitialMessageDispatch(dispatch)) {
      return false
    }
    tabSnapshotWriter.upsert(file.toSnapshot())
    return true
  }
}

internal fun interface AgentChatTabSnapshotWriter {
  suspend fun upsert(snapshot: AgentChatTabSnapshot)
}

private object ApplicationAgentChatTabSnapshotWriter : AgentChatTabSnapshotWriter {
  override suspend fun upsert(snapshot: AgentChatTabSnapshot) {
    serviceAsync<AgentChatTabsService>().upsert(snapshot)
  }
}

private const val NEW_THREAD_QUICK_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD_QUICK
private const val NEW_THREAD_POPUP_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD_POPUP

internal interface AgentChatTerminalTab {
  val component: JComponent
  val preferredFocusableComponent: JComponent
  val coroutineScope: CoroutineScope
  val sessionState: StateFlow<TerminalViewSessionState>
  val keyEventsFlow: Flow<TerminalKeyEvent>
  val terminalView: TerminalView?
    get() = null

  suspend fun awaitInitialMessageReadiness(timeoutMs: Long, idleMs: Long): AgentChatTerminalInputReadiness

  fun sendText(text: String, shouldExecute: Boolean)
}

internal enum class AgentChatTerminalInputReadiness {
  READY,
  TIMEOUT,
  TERMINATED,
}

internal interface AgentChatTerminalTabs {
  fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab

  fun closeTab(project: Project, tab: AgentChatTerminalTab)
}

private object ToolWindowAgentChatTerminalTabs : AgentChatTerminalTabs {
  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    val startupLaunchSpec = file.consumeStartupLaunchSpec()
    val terminalTab = TerminalToolWindowTabsManager.getInstance(project)
      .createTabBuilder()
      .shouldAddToToolWindow(false)
      .deferSessionStartUntilUiShown(true)
      .workingDirectory(file.projectPath)
      .processType(TerminalProcessType.NON_SHELL)
      .tabName(file.threadTitle)
      .shellCommand(startupLaunchSpec.command)
      .envVariables(startupLaunchSpec.envVariables)
      .createTab()
    return ToolWindowAgentChatTerminalTab(
      delegate = terminalTab,
      projectPath = file.projectPath,
      provider = file.provider,
    )
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    val toolWindowTab = (tab as? ToolWindowAgentChatTerminalTab)?.delegate ?: return
    closeTerminalToolWindowTab(project, toolWindowTab)
  }
}

internal fun closeTerminalToolWindowTab(
  project: Project,
  tab: TerminalToolWindowTab,
  managerProvider: (Project) -> TerminalToolWindowTabsManager = TerminalToolWindowTabsManager::getInstance,
) {
  val content = tab.content
  if (content.manager != null) {
    managerProvider(project).closeTab(tab)
  }
  else {
    content.release()
  }
}

private class ToolWindowAgentChatTerminalTab(
  val delegate: TerminalToolWindowTab,
  private val projectPath: String,
  private val provider: AgentSessionProvider?,
) : AgentChatTerminalTab {
  override val component: JComponent
    get() = delegate.content.component

  override val preferredFocusableComponent: JComponent
    get() = delegate.view.preferredFocusableComponent

  override val coroutineScope: CoroutineScope
    get() = delegate.view.coroutineScope

  override val sessionState: StateFlow<TerminalViewSessionState>
    get() = delegate.view.sessionState

  override val keyEventsFlow: Flow<TerminalKeyEvent>
    get() = delegate.view.keyEventsFlow

  override val terminalView: TerminalView
    get() = delegate.view

  override suspend fun awaitInitialMessageReadiness(timeoutMs: Long, idleMs: Long): AgentChatTerminalInputReadiness {
    val outputModels = delegate.view.outputModels
    return awaitTerminalInitialMessageReadiness(
      sessionState = delegate.view.sessionState,
      regularOutputModel = outputModels.regular,
      alternativeOutputModel = outputModels.alternative,
      timeoutMs = timeoutMs,
      idleMs = idleMs,
      onMeaningfulOutput = {
        if (provider != null && AgentSessionProviderBehaviors.find(provider)?.emitsScopedRefreshSignals == true) {
          notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = projectPath)
        }
      },
    )
  }

  override fun sendText(text: String, shouldExecute: Boolean) {
    val normalizedText = text.trim()
    if (normalizedText.isEmpty()) {
      return
    }
    val sendTextBuilder = delegate.view.createSendTextBuilder().useBracketedPasteMode()
    if (shouldExecute) {
      sendTextBuilder.shouldExecute()
    }
    sendTextBuilder.send(normalizedText)
  }
}

internal class AgentChatTerminalCommandTracker {
  private val lineBuffer = StringBuilder()

  fun record(event: KeyEvent): String? {
    return when (event.id) {
      KeyEvent.KEY_TYPED -> {
        val typedChar = event.keyChar
        if (!typedChar.isISOControl() && typedChar != KeyEvent.CHAR_UNDEFINED) {
          lineBuffer.append(typedChar)
        }
        null
      }

      KeyEvent.KEY_PRESSED -> when (event.keyCode) {
        KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE -> {
          if (lineBuffer.isNotEmpty()) {
            lineBuffer.deleteCharAt(lineBuffer.lastIndex)
          }
          null
        }

        KeyEvent.VK_ESCAPE -> {
          lineBuffer.setLength(0)
          null
        }

        KeyEvent.VK_ENTER -> {
          val command = lineBuffer.toString().trim()
          lineBuffer.setLength(0)
          command
        }

        else -> null
      }

      else -> null
    }
  }
}

@OptIn(FlowPreview::class)
internal suspend fun awaitTerminalInitialMessageReadiness(
  sessionState: StateFlow<TerminalViewSessionState>,
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  timeoutMs: Long,
  idleMs: Long,
  onMeaningfulOutput: () -> Unit = {},
): AgentChatTerminalInputReadiness {
  if (sessionState.value == TerminalViewSessionState.Terminated) {
    return AgentChatTerminalInputReadiness.TERMINATED
  }

  val readinessFlow = merge(
    meaningfulTerminalOutputFlow(
      regularOutputModel = regularOutputModel,
      alternativeOutputModel = alternativeOutputModel,
      onMeaningfulOutput = onMeaningfulOutput,
    )
      .debounce(idleMs.milliseconds)
      .map { AgentChatTerminalInputReadiness.READY },
    sessionState
      .filter { it == TerminalViewSessionState.Terminated }
      .map { AgentChatTerminalInputReadiness.TERMINATED },
  )

  return withTimeoutOrNull(timeoutMs.milliseconds) {
    readinessFlow.first()
  } ?: AgentChatTerminalInputReadiness.TIMEOUT
}

private fun meaningfulTerminalOutputFlow(
  regularOutputModel: TerminalOutputModel,
  alternativeOutputModel: TerminalOutputModel,
  onMeaningfulOutput: () -> Unit,
): Flow<Unit> = callbackFlow {
  val scope = this
  val outputModels = listOf(regularOutputModel, alternativeOutputModel)

  withContext(Dispatchers.EDT) {
    val listenerDisposable = scope.asDisposable()
    val listener = object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!scope.isActive || !isMeaningfulTerminalOutputChange(event)) {
          return
        }

        onMeaningfulOutput()
        scope.trySend(Unit)
      }
    }

    outputModels.forEach { model ->
      model.addListener(listenerDisposable, listener)
    }

    if (scope.isActive && outputModels.any(::hasAnyMeaningfulTerminalOutput)) {
      onMeaningfulOutput()
      scope.trySend(Unit)
    }
  }

  awaitClose()
}

private fun hasAnyMeaningfulTerminalOutput(model: TerminalOutputModel): Boolean {
  val end = model.endOffset
  val availableChars = end - model.startOffset
  val start = if (availableChars > READINESS_SCAN_LIMIT_CHARS) end - READINESS_SCAN_LIMIT_CHARS else model.startOffset
  return model.getText(start, end).any(::isMeaningfulTerminalOutputChar)
}

internal fun isMeaningfulTerminalOutputChange(event: TerminalContentChangeEvent): Boolean {
  return !event.isTypeAhead && !event.isTrimming && event.newText.isNotEmpty() && event.newText.any(::isMeaningfulTerminalOutputChar)
}

private fun isMeaningfulTerminalOutputChar(char: Char): Boolean {
  return !char.isWhitespace() && char != '%'
}

private const val INITIAL_MESSAGE_READINESS_TIMEOUT_MS: Long = 2_000
private const val INITIAL_MESSAGE_OUTPUT_IDLE_MS: Long = 250
private const val READINESS_SCAN_LIMIT_CHARS: Long = 8_192
