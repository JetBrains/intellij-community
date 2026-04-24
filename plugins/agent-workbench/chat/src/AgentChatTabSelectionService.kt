// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AgentChatTabSelection(
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val subAgentId: String?,
)

@Service(Service.Level.PROJECT)
class AgentChatTabSelectionService(private val project: Project, cs: CoroutineScope) {
  val selectedChatTab: StateFlow<AgentChatTabSelection?> = flow {
    val manager = project.serviceAsync<FileEditorManager>()
    emitAll(manager.selectedEditorFlow)
  }
    .map { selectedEditor -> selectedEditor.toAgentChatTabSelection() }
    .distinctUntilChanged()
    .stateIn(cs, SharingStarted.Eagerly, null)

  fun hasOpenChatTabs(): Boolean {
    return hasOpenAgentChatFiles(FileEditorManager.getInstance(project).openFiles)
  }
}

internal fun FileEditor?.toAgentChatTabSelection(): AgentChatTabSelection? {
  val file = this?.file as? AgentChatVirtualFile ?: return null
  return AgentChatTabSelection(
    projectPath = file.projectPath,
    threadIdentity = file.threadIdentity,
    threadId = file.threadId,
    subAgentId = file.subAgentId,
  )
}

internal fun hasOpenAgentChatFiles(openFiles: Array<VirtualFile>): Boolean {
  return openFiles.any { file -> file is AgentChatVirtualFile }
}
