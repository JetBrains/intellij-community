// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("unused")
internal class AgentChatFileEditorProvider : AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is AgentChatVirtualFile

  override fun acceptRequiresReadAction(): Boolean = false

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    return withContext(Dispatchers.EDT) {
      createChatEditor(project, file as AgentChatVirtualFile)
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return createChatEditor(project, file as AgentChatVirtualFile)
  }

  override fun getEditorTypeId(): String = "agent.workbench-chat-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  private fun createChatEditor(project: Project, file: AgentChatVirtualFile): FileEditor {
    val terminalManager = TerminalToolWindowTabsManager.getInstance(project)
    val tab = terminalManager.createTabBuilder()
      .shouldAddToToolWindow(false)
      .workingDirectory(file.projectPath)
      .tabName(file.name)
      .shellCommand(file.shellCommand)
      .createTab()
    return AgentChatFileEditor(file, tab)
  }
}
