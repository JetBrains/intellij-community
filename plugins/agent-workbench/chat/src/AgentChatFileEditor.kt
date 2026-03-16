// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import java.beans.PropertyChangeListener
import javax.swing.JComponent

internal class AgentChatFileEditor(
  private val file: AgentChatVirtualFile,
  private val tab: TerminalToolWindowTab,
) : UserDataHolderBase(), FileEditor {
  private val component = tab.content.component

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent = tab.view.preferredFocusableComponent

  override fun getName(): String = file.name

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun getFile(): AgentChatVirtualFile = file

  override fun dispose() {
    Disposer.dispose(tab.content)
  }
}
