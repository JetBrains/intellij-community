// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

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
import org.jetbrains.annotations.ApiStatus

data class AgentThreadViewTabSelection(
  @JvmField val projectPath: String,
  @JvmField val threadIdentity: String,
  @JvmField val threadId: String,
  @JvmField val subAgentId: String?,
)

@Service(Service.Level.PROJECT)
class AgentThreadViewTabSelectionService(private val project: Project, cs: CoroutineScope) {
  val selectedThreadViewTab: StateFlow<AgentThreadViewTabSelection?> = flow {
    val manager = project.serviceAsync<FileEditorManager>()
    emitAll(manager.selectedEditorFlow)
  }
    .map { selectedEditor -> selectedEditor.toAgentThreadViewTabSelection() }
    .distinctUntilChanged()
    .stateIn(cs, SharingStarted.Eagerly, null)

  fun hasOpenThreadViewTabs(): Boolean {
    return hasOpenAgentThreadViewFiles(FileEditorManager.getInstance(project).openFiles)
  }
}

internal fun FileEditor?.toAgentThreadViewTabSelection(): AgentThreadViewTabSelection? {
  return this?.file.toAgentThreadViewTabSelection()
}

@ApiStatus.Internal
fun VirtualFile?.toAgentThreadViewTabSelection(): AgentThreadViewTabSelection? {
  val file = this as? AgentThreadViewVirtualFile ?: return null
  return AgentThreadViewTabSelection(
    projectPath = file.projectPath,
    threadIdentity = file.threadIdentity,
    threadId = file.threadId,
    subAgentId = file.subAgentId,
  )
}

internal fun hasOpenAgentThreadViewFiles(openFiles: Array<VirtualFile>): Boolean {
  return openFiles.any { file -> file is AgentThreadViewVirtualFile }
}
