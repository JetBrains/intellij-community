// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val terminalTabs: AgentChatTerminalTabs = ToolWindowAgentChatTerminalTabs,
) : UserDataHolderBase(), FileEditor {
  private val component = JPanel(BorderLayout())
  private val editorTabActions: ActionGroup? by lazy {
    val actionManager = ActionManager.getInstance()
    val actions = listOfNotNull(
      actionManager.getAction(NEW_THREAD_QUICK_FROM_EDITOR_TAB_ACTION_ID),
      actionManager.getAction(NEW_THREAD_POPUP_FROM_EDITOR_TAB_ACTION_ID),
      actionManager.getAction(BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB_ACTION_ID),
    )
    if (actions.isEmpty()) {
      return@lazy null
    }
    if (actions.size == 1) {
      val singleAction = actions.single()
      return@lazy singleAction as? ActionGroup ?: DefaultActionGroup(singleAction)
    }
    DefaultActionGroup(actions)
  }
  private var tab: AgentChatTerminalTab? = null
  private var initializationStarted: Boolean = false
  private var disposed: Boolean = false
  private var pendingInitialMessageJob: Job? = null

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
    pendingInitialMessageJob?.cancel()
    pendingInitialMessageJob = null
    tab?.let { terminalTab ->
      terminalTabs.closeTab(project, terminalTab)
    }
    tab = null
    component.removeAll()
  }

  private fun ensureInitialized() {
    if (initializationStarted || disposed) {
      return
    }
    initializationStarted = true
    try {
      val createdTab = terminalTabs.createTab(project, file)
      tab = createdTab
      subscribePendingFirstInput(createdTab)
      scheduleInitialMessageSend(createdTab)
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

  internal fun flushPendingInitialMessageIfInitialized() {
    val initializedTab = tab ?: return
    scheduleInitialMessageSend(initializedTab)
  }

  private fun subscribePendingFirstInput(createdTab: AgentChatTerminalTab) {
    if (!file.isPendingThread || file.provider != AgentSessionProvider.CODEX) {
      return
    }
    createdTab.coroutineScope.launch {
      val tabsService = serviceAsync<AgentChatTabsService>()
      createdTab.keyEventsFlow.collectLatest {
        if (!file.markPendingFirstInputAtMsIfAbsent(System.currentTimeMillis())) {
          return@collectLatest
        }
        tabsService.upsert(file.toSnapshot())
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
      when (createdTab.awaitInitialMessageReadiness(
        timeoutMs = INITIAL_MESSAGE_READINESS_TIMEOUT_MS,
        idleMs = INITIAL_MESSAGE_OUTPUT_IDLE_MS,
      )) {
        AgentChatTerminalInputReadiness.READY,
        AgentChatTerminalInputReadiness.TIMEOUT
        -> sendInitialMessageIfReady(createdTab)
        AgentChatTerminalInputReadiness.TERMINATED -> Unit
      }
    }.also { job ->
      job.invokeOnCompletion {
        if (pendingInitialMessageJob === job) {
          pendingInitialMessageJob = null
        }
      }
    }
  }

  private fun sendInitialMessageIfReady(createdTab: AgentChatTerminalTab): Boolean {
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
    serviceIfCreated<AgentChatTabsService>()?.upsert(file.toSnapshot())
    return true
  }
}

private const val NEW_THREAD_QUICK_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD_QUICK
private const val NEW_THREAD_POPUP_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD_POPUP
private const val BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB_ACTION_ID: String =
  AgentWorkbenchActionIds.Sessions.BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB

internal interface AgentChatTerminalTab {
  val component: JComponent
  val preferredFocusableComponent: JComponent
  val coroutineScope: CoroutineScope
  val sessionState: StateFlow<TerminalViewSessionState>
  val keyEventsFlow: Flow<*>

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
    return ToolWindowAgentChatTerminalTab(terminalTab)
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
) : AgentChatTerminalTab {
  override val component: JComponent
    get() = delegate.content.component

  override val preferredFocusableComponent: JComponent
    get() = delegate.view.preferredFocusableComponent

  override val coroutineScope: CoroutineScope
    get() = delegate.view.coroutineScope

  override val sessionState: StateFlow<TerminalViewSessionState>
    get() = delegate.view.sessionState

  override val keyEventsFlow: Flow<*>
    get() = delegate.view.keyEventsFlow

  override suspend fun awaitInitialMessageReadiness(timeoutMs: Long, idleMs: Long): AgentChatTerminalInputReadiness {
    if (delegate.view.sessionState.value == TerminalViewSessionState.Terminated) {
      return AgentChatTerminalInputReadiness.TERMINATED
    }

    val readinessDeferred = CompletableDeferred<AgentChatTerminalInputReadiness>()
    val outputListenerDisposable = Disposer.newDisposable("agent-chat-initial-message-readiness")
    var idleJob: Job? = null
    var terminationJob: Job? = null
    var sawMeaningfulOutput = false

    fun rescheduleIdleCompletion() {
      if (!sawMeaningfulOutput || readinessDeferred.isCompleted) {
        return
      }
      idleJob?.cancel()
      idleJob = delegate.view.coroutineScope.launch {
        delay(idleMs.milliseconds)
        readinessDeferred.complete(AgentChatTerminalInputReadiness.READY)
      }
    }

    try {
      withContext(Dispatchers.EDT) {
        val listener = object : TerminalOutputModelListener {
          override fun afterContentChanged(event: TerminalContentChangeEvent) {
            if (event.isTypeAhead || event.isTrimming || readinessDeferred.isCompleted || event.newText.isEmpty()) {
              return
            }
            if (event.newText.any(::isMeaningfulTerminalOutputChar)) {
              sawMeaningfulOutput = true
            }
            if (sawMeaningfulOutput) {
              rescheduleIdleCompletion()
            }
          }
        }
        val outputModels = delegate.view.outputModels
        outputModels.regular.addListener(outputListenerDisposable, listener)
        outputModels.alternative.addListener(outputListenerDisposable, listener)

        if (hasAnyMeaningfulTerminalOutput(outputModels.regular) || hasAnyMeaningfulTerminalOutput(outputModels.alternative)) {
          sawMeaningfulOutput = true
          rescheduleIdleCompletion()
        }
      }

      terminationJob = delegate.view.coroutineScope.launch {
        delegate.view.sessionState.first { it == TerminalViewSessionState.Terminated }
        readinessDeferred.complete(AgentChatTerminalInputReadiness.TERMINATED)
      }

      return withTimeoutOrNull(timeoutMs.milliseconds) {
        readinessDeferred.await()
      } ?: AgentChatTerminalInputReadiness.TIMEOUT
    }
    finally {
      idleJob?.cancel()
      terminationJob?.cancel()
      withContext(Dispatchers.EDT) {
        Disposer.dispose(outputListenerDisposable)
      }
    }
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

private fun hasAnyMeaningfulTerminalOutput(model: TerminalOutputModel): Boolean {
  val end = model.endOffset
  val availableChars = end - model.startOffset
  val start = if (availableChars > READINESS_SCAN_LIMIT_CHARS) end - READINESS_SCAN_LIMIT_CHARS else model.startOffset
  return model.getText(start, end).any(::isMeaningfulTerminalOutputChar)
}

private fun isMeaningfulTerminalOutputChar(char: Char): Boolean {
  return !char.isWhitespace() && char != '%'
}

private const val INITIAL_MESSAGE_READINESS_TIMEOUT_MS: Long = 2_000
private const val INITIAL_MESSAGE_OUTPUT_IDLE_MS: Long = 250
private const val READINESS_SCAN_LIMIT_CHARS: Long = 8_192
