// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
) : UserDataHolderBase(), FileEditor {
  private val component = JPanel(BorderLayout())
  private var tab: TerminalToolWindowTab? = null
  private var initializationStarted: Boolean = false
  private var disposed: Boolean = false

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    ensureInitialized()
    return tab?.view?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = file.threadTitle

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
      component.removeAll()
      component.add(createdTab.content.component, BorderLayout.CENTER)
      component.revalidate()
      component.repaint()
    }
    catch (t: Throwable) {
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, t)
    }
  }
}
