// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.project.projectId
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * This listener passes tab events from frontend to backend.
 *
 * It is not implemented with [com.intellij.execution.ui.RunContentWithExecutorListener], because it is not invoked at frontend.
 */
private class XDebugRunContentListener(private val project: Project) : ToolWindowManagerListener {
  override fun toolWindowsRegistered(ids: List<String?>, toolWindowManager: ToolWindowManager) {
    if (ToolWindowId.DEBUG !in ids) return

    val debugToolWindow = toolWindowManager.getToolWindow(ToolWindowId.DEBUG) ?: return
    val selectedSessionId = MutableSharedFlow<XDebugSessionId?>(extraBufferCapacity = Int.MAX_VALUE)
    debugToolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        val descriptor = getDescriptor(event) ?: return
        val sessionId = XDebugManagerProxy.getInstance().getSessionIdByContentDescriptor(project, descriptor)
        selectedSessionId.tryEmit(sessionId)
      }

      override fun contentRemoved(event: ContentManagerEvent) {
        val descriptor = getDescriptor(event) ?: return
        val sessionId = XDebugManagerProxy.getInstance().getSessionIdByContentDescriptor(project, descriptor) ?: return
        project.service<XDebugRunContentListenerCoroutineScopeProvider>().cs.launch {
          XDebuggerManagerApi.getInstance().sessionTabClosed(sessionId)
        }
      }

      private fun getDescriptor(event: ContentManagerEvent): RunContentDescriptor? {
        if (event.operation != ContentManagerEvent.ContentOperation.add) return null
        return RunContentManagerImpl.getRunContentDescriptorByContent(event.content)
      }
    })
    project.service<XDebugRunContentListenerCoroutineScopeProvider>().cs.launch {
      selectedSessionId.collectLatest { sessionId ->
        XDebuggerManagerApi.getInstance().sessionTabSelected(project.projectId(), sessionId)
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class XDebugRunContentListenerCoroutineScopeProvider(val cs: CoroutineScope)
