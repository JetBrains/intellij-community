// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.statistic

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.WindowInfoImpl
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class ToolWindowStateListener(private val project: Project) : ToolWindowManagerListener {
  override fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow, changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
    project.service<ToolWindowStateCollector>().stateChanged(toolWindowManager, toolWindow, changeType)
  }

  override fun toolWindowUnregistered(id: String, toolWindow: ToolWindow) {
    project.service<ToolWindowStateCollector>().toolWindowUnregistered(id)
  }
}


@Service(Service.Level.PROJECT)
private class ToolWindowStateCollector(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val windowsSize = ConcurrentHashMap<String, Int>()
  private val collectors = ConcurrentHashMap<String, Collector>()

  private fun reportResizeEvent(toolWindowManager: ToolWindowManager, toolWindow: ToolWindowImpl) {
    val size = if (toolWindow.anchor.isHorizontal) toolWindow.component.height else toolWindow.component.width
    val oldSize = windowsSize[toolWindow.id]
    // The == null check isn't really necessary; it's simply to clearly state our intent here.
    if (oldSize == null || oldSize != size) { //we don't care about the changed height of vertical windows or the width of horizontal ones
      val windowInfo = toolWindow.windowInfo as? WindowInfoImpl ?: return
      ToolWindowCollector.getInstance().recordResized(project, windowInfo, toolWindowManager.isMaximized(toolWindow))
      windowsSize[toolWindow.id] = size
    }
  }

  fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow, changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
    if (changeType == ToolWindowManagerListener.ToolWindowManagerEventType.MovedOrResized && toolWindow.type == ToolWindowType.DOCKED && toolWindow is ToolWindowImpl) {
      // Do not use getOrPut here, as it may check for the existing value, not find one,
      // create a new collector and then check again.
      // If a new value appears in the meantime, then the created collector will be dropped and ignored.
      collectors.computeIfAbsent(toolWindow.id) { Collector(toolWindowManager, toolWindow) }.ping()
    }
  }

  fun toolWindowUnregistered(id: String) {
    collectors.remove(id)?.cancel()
  }

  @OptIn(FlowPreview::class)
  private inner class Collector(toolWindowManager: ToolWindowManager, toolWindow: ToolWindowImpl) {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val job = coroutineScope.launch(CoroutineName("Tool window resize collector for ${toolWindow.id}") + Dispatchers.EDT) {
      flow.debounce(DEBOUNCE_TIMEOUT_MS).collect {
        reportResizeEvent(toolWindowManager, toolWindow)
      }
    }

    fun ping() {
      check(flow.tryEmit(Unit))
    }

    fun cancel() {
      job.cancel()
    }
  }
}

private const val DEBOUNCE_TIMEOUT_MS = 500L
