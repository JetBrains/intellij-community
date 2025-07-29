// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectIdOrNull
import com.intellij.vcs.git.rpc.GitWidgetApi
import com.intellij.vcs.git.rpc.GitWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@Service(Service.Level.PROJECT)
internal class GitWidgetStateHolder(private val project: Project, cs: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<GitWidgetState> = flow {
    val manager = project.serviceAsync<FileEditorManager>()
    emitAll(manager.selectedEditorFlow)
  }.flatMapLatest { selectedEditor ->
    val projectId = project.projectIdOrNull() ?: return@flatMapLatest flowOf(GitWidgetState.DoNotShow)
    GitWidgetApi.getInstance().getWidgetState(projectId, selectedEditor?.file?.rpcId())
  }.stateIn(cs, SharingStarted.Eagerly, GitWidgetState.DoNotShow)

  companion object {
    fun getInstance(project: Project): GitWidgetStateHolder = project.service()
  }
}