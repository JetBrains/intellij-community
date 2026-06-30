// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

// @spec community/plugins/agent-workbench/spec/thread-view/agent-thread-view.spec.md

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
internal class AgentThreadViewFileEditorProvider : AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is AgentThreadViewVirtualFile

  override fun acceptRequiresReadAction(): Boolean = false

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val threadViewFile = file as AgentThreadViewVirtualFile
    return withContext(Dispatchers.EDT) {
      createAgentThreadViewFileEditor(project = project, file = threadViewFile, editorCoroutineScope = editorCoroutineScope)
    }
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val threadViewFile = file as AgentThreadViewVirtualFile
    return createAgentThreadViewFileEditor(project = project, file = threadViewFile, editorCoroutineScope = null)
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    return readAgentThreadViewFileEditorState(sourceElement, file)
  }

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is AgentThreadViewFileEditorState) {
      writeAgentThreadViewFileEditorState(state, targetElement)
    }
  }

  override fun getEditorTypeId(): String = "agent.workbench-thread-view-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}

internal fun interface AgentThreadViewFileEditorFactory {
  fun create(project: Project, file: AgentThreadViewVirtualFile, editorCoroutineScope: CoroutineScope?): AgentThreadViewFileEditor
}

private val agentThreadViewFileEditorFactoryOverrideForTests: AtomicReference<AgentThreadViewFileEditorFactory?> = AtomicReference(null)

internal fun registerAgentThreadViewFileEditorFactoryOverrideForTests(factory: AgentThreadViewFileEditorFactory, parentDisposable: Disposable) {
  agentThreadViewFileEditorFactoryOverrideForTests.set(factory)
  Disposer.register(parentDisposable) {
    agentThreadViewFileEditorFactoryOverrideForTests.compareAndSet(factory, null)
  }
}

private fun createAgentThreadViewFileEditor(
  project: Project,
  file: AgentThreadViewVirtualFile,
  editorCoroutineScope: CoroutineScope?,
): AgentThreadViewFileEditor {
  return agentThreadViewFileEditorFactoryOverrideForTests.get()?.create(project, file, editorCoroutineScope)
         ?: AgentThreadViewFileEditor(project = project, file = file, editorCoroutineScope = editorCoroutineScope)
}

internal fun validateAgentThreadViewFile(file: AgentThreadViewVirtualFile): String? {
  return when {
    file.projectPath.isBlank() -> AgentThreadViewBundle.message("thread.view.restore.validation.project.path")
    file.threadIdentity.isBlank() -> AgentThreadViewBundle.message("thread.view.restore.validation.thread.identity")
    file.provider == null || file.sessionId.isBlank() -> AgentThreadViewBundle.message("thread.view.restore.validation.thread.identity")
    else -> null
  }
}
