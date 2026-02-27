// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.CancellationException
import javax.swing.JComponent
import javax.swing.JPanel

private const val NEW_THREAD_FROM_EDITOR_TAB_ACTION_ID = "AgentWorkbenchSessions.TreePopup.NewThread"
private const val BIND_PENDING_CODEX_THREAD_FROM_EDITOR_TAB_ACTION_ID = "AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab"

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
) : UserDataHolderBase(), FileEditor {
  private val component = JPanel(BorderLayout())
  private val editorTabActions: ActionGroup? by lazy {
    val actionManager = ActionManager.getInstance()
    val actions = listOfNotNull(
      actionManager.getAction(NEW_THREAD_FROM_EDITOR_TAB_ACTION_ID),
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
  private var tab: TerminalToolWindowTab? = null
  private var initializationStarted: Boolean = false
  private var disposed: Boolean = false

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    ensureInitialized()
    return tab?.view?.preferredFocusableComponent ?: component
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
      TerminalToolWindowTabsManager.getInstance(project).closeTab(terminalTab)
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
      val terminalManager = TerminalToolWindowTabsManager.getInstance(project)
      val createdTab = terminalManager.createTabBuilder()
        .shouldAddToToolWindow(false)
        .workingDirectory(file.projectPath)
        .processType(TerminalProcessType.NON_SHELL)
        .tabName(file.threadTitle)
        .shellCommand(file.shellCommand)
        .createTab()
      tab = createdTab
      subscribePendingFirstInput(createdTab)
      component.removeAll()
      component.add(createdTab.content.component, BorderLayout.CENTER)
      component.revalidate()
      component.repaint()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, t)
    }
  }

  private fun subscribePendingFirstInput(createdTab: TerminalToolWindowTab) {
    if (!file.isPendingThread || file.provider != AgentSessionProvider.CODEX) {
      return
    }
    val tabsService = service<AgentChatTabsService>()
    createdTab.view.coroutineScope.launch {
      createdTab.view.keyEventsFlow.collectLatest {
        if (!file.markPendingFirstInputAtMsIfAbsent(System.currentTimeMillis())) {
          return@collectLatest
        }
        tabsService.upsert(file.toSnapshot())
      }
    }
  }
}
