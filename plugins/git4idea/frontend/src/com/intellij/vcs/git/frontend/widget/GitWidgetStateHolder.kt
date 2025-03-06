// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.vcs.git.shared.isRdBranchWidgetEnabled
import com.intellij.vcs.git.shared.rpc.GitWidgetApi
import com.intellij.vcs.git.shared.rpc.GitWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class GitWidgetStateHolder(private val project: Project, private val cs: CoroutineScope) {
  var currentState: GitWidgetState = GitWidgetState.DoNotShow
    private set

  @Volatile
  private var stateUpdateJob: Job? = null

  init {
    if (Registry.isRdBranchWidgetEnabled()) {
      project.messageBus.connect(cs)
        .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun selectionChanged(event: FileEditorManagerEvent) {
            initStateUpdate(event.newFile)
          }
        })
    }
  }

  internal fun initStateUpdate(selectedFile: VirtualFile?) {
    synchronized(this) {
      stateUpdateJob?.cancel()
      stateUpdateJob = cs.launch {
        GitWidgetApi.getInstance().getWidgetState(project.projectId(), selectedFile?.rpcId()).collect {
          currentState = it
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): GitWidgetStateHolder = project.service()
  }
}
