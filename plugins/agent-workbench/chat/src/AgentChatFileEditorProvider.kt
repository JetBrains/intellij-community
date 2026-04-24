// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

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
    val chatFile = file as AgentChatVirtualFile
    val validationError = validate(chatFile)
    if (validationError == null) {
      return withContext(Dispatchers.UI) {
        AgentChatFileEditor(project = project, file = chatFile)
      }
    }
    else {
      return withContext(Dispatchers.EDT) {
        handleValidationError(project, chatFile, validationError)
      }
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val chatFile = file as AgentChatVirtualFile
    val validationError = validate(chatFile)
    if (validationError == null) {
      return AgentChatFileEditor(project = project, file = chatFile)
    }
    else {
      return handleValidationError(project, chatFile, validationError)
    }
  }

  override fun getEditorTypeId(): String = "agent.workbench-chat-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}

private fun handleValidationError(project: Project, file: AgentChatVirtualFile, validationError: String): FileEditor {
  forgetAgentChatTabMetadata(file.tabKey)
  AgentChatRestoreNotificationService.reportRestoreFailure(project, file, validationError)
  if (!project.isDisposed) {
    FileEditorManager.getInstance(project).closeFile(file)
  }
  return AgentChatUnavailableFileEditor(file)
}

private fun validate(file: AgentChatVirtualFile): String? {
  return when {
    file.projectPath.isBlank() -> AgentChatBundle.message("chat.restore.validation.project.path")
    file.threadIdentity.isBlank() -> AgentChatBundle.message("chat.restore.validation.thread.identity")
    file.shellCommand.isEmpty() -> AgentChatBundle.message("chat.restore.validation.shell.command")
    else -> null
  }
}

private class AgentChatUnavailableFileEditor(
  private val file: AgentChatVirtualFile,
) : UserDataHolderBase(), FileEditor {
  private val component = JPanel()

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent = component

  override fun getName(): String = file.threadTitle

  override fun setState(state: FileEditorState) = Unit

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = false

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun getFile(): AgentChatVirtualFile = file

  override fun dispose() = Unit
}
