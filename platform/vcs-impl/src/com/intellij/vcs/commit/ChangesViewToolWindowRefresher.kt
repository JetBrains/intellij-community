// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.RefreshAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private class ChangesViewToolWindowRefresher(private val project: Project, coroutineScope: CoroutineScope) : ToolWindowManagerListener {
  private val refreshFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      @OptIn(FlowPreview::class)
      refreshFlow.debounce(300.milliseconds).collect {
        RefreshAction.doRefreshSuspending(project)
      }
    }
  }

  override fun toolWindowShown(toolWindow: ToolWindow) {
    if (ChangesViewContentManager.getToolWindowIdFor(project, LOCAL_CHANGES) == toolWindow.id) {
      refreshFlow.tryEmit(Unit)
    }
  }
}