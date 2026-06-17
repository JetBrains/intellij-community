// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-editor.spec.md

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import java.util.concurrent.atomic.AtomicReference

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
    return withContext(Dispatchers.EDT) {
      createAgentChatFileEditor(project = project, file = chatFile, editorCoroutineScope = editorCoroutineScope)
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val chatFile = file as AgentChatVirtualFile
    return createAgentChatFileEditor(project = project, file = chatFile, editorCoroutineScope = null)
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    return readAgentChatFileEditorState(sourceElement, file)
  }

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is AgentChatFileEditorState) {
      writeAgentChatFileEditorState(state, targetElement)
    }
  }

  override fun getEditorTypeId(): String = "agent.workbench-chat-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}

internal fun interface AgentChatFileEditorFactory {
  fun create(project: Project, file: AgentChatVirtualFile, editorCoroutineScope: CoroutineScope?): AgentChatFileEditor
}

private val agentChatFileEditorFactoryOverrideForTests: AtomicReference<AgentChatFileEditorFactory?> = AtomicReference(null)

internal fun registerAgentChatFileEditorFactoryOverrideForTests(factory: AgentChatFileEditorFactory, parentDisposable: Disposable) {
  agentChatFileEditorFactoryOverrideForTests.set(factory)
  Disposer.register(parentDisposable) {
    agentChatFileEditorFactoryOverrideForTests.compareAndSet(factory, null)
  }
}

private fun createAgentChatFileEditor(
  project: Project,
  file: AgentChatVirtualFile,
  editorCoroutineScope: CoroutineScope?,
): AgentChatFileEditor {
  return agentChatFileEditorFactoryOverrideForTests.get()?.create(project, file, editorCoroutineScope)
         ?: AgentChatFileEditor(project = project, file = file, editorCoroutineScope = editorCoroutineScope)
}

internal fun validateAgentChatFile(file: AgentChatVirtualFile): String? {
  return when {
    file.projectPath.isBlank() -> AgentChatBundle.message("chat.restore.validation.project.path")
    file.threadIdentity.isBlank() -> AgentChatBundle.message("chat.restore.validation.thread.identity")
    file.provider == null || file.sessionId.isBlank() -> AgentChatBundle.message("chat.restore.validation.thread.identity")
    else -> null
  }
}
