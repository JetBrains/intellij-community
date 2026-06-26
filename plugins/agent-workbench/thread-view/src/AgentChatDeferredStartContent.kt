// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class AgentChatDeferredStartContent(
  @JvmField val component: JComponent,
  @JvmField val preferredFocusedComponent: JComponent? = null,
  private val disposeContent: () -> Unit = {},
) {
  private var disposed: Boolean = false

  fun dispose() {
    if (disposed) {
      return
    }
    disposed = true
    disposeContent()
  }
}

@ApiStatus.Internal
fun installAgentChatDeferredStartContent(
  project: Project,
  file: VirtualFile,
  content: AgentChatDeferredStartContent,
): Boolean {
  val chatFile = file as? AgentChatVirtualFile ?: return false
  chatFile.replaceDeferredStartContent(content)
  FileEditorManager.getInstance(project).getEditors(chatFile).forEach { editor ->
    (editor as? AgentChatFileEditor)?.refreshForFileStateChange()
  }
  return true
}

private val AGENT_CHAT_DEFERRED_START_CONTENT_KEY: Key<AgentChatDeferredStartContent> =
  Key.create("agent.workbench.chat.deferred.start.content")

internal fun AgentChatVirtualFile.deferredStartContent(): AgentChatDeferredStartContent? {
  return getUserData(AGENT_CHAT_DEFERRED_START_CONTENT_KEY)
}

internal fun AgentChatVirtualFile.replaceDeferredStartContent(content: AgentChatDeferredStartContent?) {
  val previous = deferredStartContent()
  if (previous === content) {
    return
  }
  putUserData(AGENT_CHAT_DEFERRED_START_CONTENT_KEY, content)
  previous?.dispose()
}

internal fun AgentChatVirtualFile.clearDeferredStartContent() {
  replaceDeferredStartContent(null)
}
