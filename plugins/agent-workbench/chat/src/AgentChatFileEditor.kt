// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JPanel

private const val NEW_THREAD_QUICK_FROM_EDITOR_TAB_ACTION_ID = "AgentWorkbenchSessions.EditorTab.NewThreadQuick"
private const val NEW_THREAD_POPUP_FROM_EDITOR_TAB_ACTION_ID = "AgentWorkbenchSessions.EditorTab.NewThreadPopup"
private const val BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB_ACTION_ID = "AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab"

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
}

internal interface AgentChatTerminalTab {
  val component: JComponent
  val preferredFocusableComponent: JComponent
  val coroutineScope: CoroutineScope
  val keyEventsFlow: Flow<*>
}

internal interface AgentChatTerminalTabs {
  fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab

  fun closeTab(project: Project, tab: AgentChatTerminalTab)
}

private object ToolWindowAgentChatTerminalTabs : AgentChatTerminalTabs {
  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    val terminalTab = TerminalToolWindowTabsManager.getInstance(project)
      .createTabBuilder()
      .shouldAddToToolWindow(false)
      .deferSessionStartUntilUiShown(true)
      .workingDirectory(file.projectPath)
      .processType(TerminalProcessType.NON_SHELL)
      .tabName(file.threadTitle)
      .shellCommand(file.shellCommand)
      .createTab()
    return ToolWindowAgentChatTerminalTab(terminalTab)
  }

  override fun closeTab(project: Project, tab: AgentChatTerminalTab) {
    val toolWindowTab = (tab as? ToolWindowAgentChatTerminalTab)?.delegate ?: return
    TerminalToolWindowTabsManager.getInstance(project).closeTab(toolWindowTab)
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

  override val keyEventsFlow: Flow<*>
    get() = delegate.view.keyEventsFlow
}
