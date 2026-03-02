// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
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
      sendInitialMessageIfNeeded(createdTab)
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

  internal fun flushPendingInitialMessageIfInitialized(): Boolean {
    val initializedTab = tab ?: return false
    return sendInitialMessageIfNeeded(initializedTab)
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

  private fun sendInitialMessageIfNeeded(createdTab: AgentChatTerminalTab): Boolean {
    val initialMessage = file.initialComposedMessage?.trim().orEmpty()
    if (initialMessage.isEmpty() || file.initialMessageSent) {
      return false
    }
    createdTab.sendText(initialMessage, shouldExecute = true)
    if (!file.markInitialMessageSent()) {
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
  val keyEventsFlow: Flow<*>

  fun sendText(text: String, shouldExecute: Boolean)
}

internal interface AgentChatTerminalTabs {
  fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab

  fun closeTab(project: Project, tab: AgentChatTerminalTab)
}

private object ToolWindowAgentChatTerminalTabs : AgentChatTerminalTabs {
  override fun createTab(project: Project, file: AgentChatVirtualFile): AgentChatTerminalTab {
    val launchSpec = resolveAgentChatTerminalLaunchSpec(
      provider = file.provider,
      command = file.consumeStartupShellCommand(),
    )
    val terminalTab = TerminalToolWindowTabsManager.getInstance(project)
      .createTabBuilder()
      .shouldAddToToolWindow(false)
      .deferSessionStartUntilUiShown(true)
      .workingDirectory(file.projectPath)
      .processType(TerminalProcessType.NON_SHELL)
      .tabName(file.threadTitle)
      .shellCommand(launchSpec.command)
      .envVariables(launchSpec.envVariables)
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

internal data class AgentChatTerminalLaunchSpec(
  @JvmField val command: List<String>,
  @JvmField val envVariables: Map<String, String>,
)

private const val CODEX_AUTO_UPDATE_CONFIG: String = "check_for_update_on_startup=false"
private const val CLAUDE_DISABLE_AUTO_UPDATER_ENV: String = "DISABLE_AUTOUPDATER"
private const val CLAUDE_DISABLE_AUTO_UPDATER_VALUE: String = "1"

internal fun resolveAgentChatTerminalLaunchSpec(
  provider: AgentSessionProvider?,
  command: List<String>,
): AgentChatTerminalLaunchSpec {
  val commandWithDisabledCodexAutoUpdate = disableCodexAutoUpdateCheck(provider = provider, command = command)
  val envVariables = if (isClaudeCommand(provider = provider, command = commandWithDisabledCodexAutoUpdate)) {
    mapOf(CLAUDE_DISABLE_AUTO_UPDATER_ENV to CLAUDE_DISABLE_AUTO_UPDATER_VALUE)
  }
  else {
    emptyMap()
  }
  return AgentChatTerminalLaunchSpec(
    command = commandWithDisabledCodexAutoUpdate,
    envVariables = envVariables,
  )
}

private fun disableCodexAutoUpdateCheck(provider: AgentSessionProvider?, command: List<String>): List<String> {
  if (!isCodexCommand(provider = provider, command = command) || command.isEmpty()) {
    return command
  }

  val rewrittenCommand = ArrayList<String>(command.size + 2)
  rewrittenCommand.add(command.first())
  var index = 1
  var replaced = false
  while (index < command.size) {
    val argument = command[index]
    if (argument == "--") {
      rewrittenCommand.addAll(command.subList(index, command.size))
      break
    }
    if (argument == "-c" && index + 1 < command.size && isCodexAutoUpdateConfig(command[index + 1])) {
      if (!replaced) {
        rewrittenCommand.add("-c")
        rewrittenCommand.add(CODEX_AUTO_UPDATE_CONFIG)
        replaced = true
      }
      index += 2
      continue
    }
    rewrittenCommand.add(argument)
    index++
  }
  if (!replaced) {
    rewrittenCommand.add(1, CODEX_AUTO_UPDATE_CONFIG)
    rewrittenCommand.add(1, "-c")
  }
  return rewrittenCommand
}

private fun isCodexAutoUpdateConfig(argument: String): Boolean {
  return argument.startsWith("check_for_update_on_startup=")
}

private fun isCodexCommand(provider: AgentSessionProvider?, command: List<String>): Boolean {
  if (provider == AgentSessionProvider.CODEX) {
    return true
  }
  val executable = command.firstOrNull() ?: return false
  return hasExecutableName(executablePath = executable, executableName = "codex")
}

private fun isClaudeCommand(provider: AgentSessionProvider?, command: List<String>): Boolean {
  if (provider == AgentSessionProvider.CLAUDE) {
    return true
  }
  val executable = command.firstOrNull() ?: return false
  return hasExecutableName(executablePath = executable, executableName = "claude")
}

private fun hasExecutableName(executablePath: String, executableName: String): Boolean {
  val fileName = executablePath.substringAfterLast('/').substringAfterLast('\\')
  return fileName.equals(executableName, ignoreCase = true) || fileName.equals("$executableName.exe", ignoreCase = true)
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
