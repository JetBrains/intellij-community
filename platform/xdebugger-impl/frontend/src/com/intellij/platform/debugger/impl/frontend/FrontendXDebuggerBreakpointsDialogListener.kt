// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory
import com.intellij.xdebugger.impl.rpc.XDebuggerBreakpointsDialogApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG = fileLogger()

internal fun subscribeOnBreakpointsDialogRequest(project: Project) {
  project.service<FrontendXDebuggerBreakpointsDialogListenerCoroutineScope>().cs.launch {
    XDebuggerBreakpointsDialogApi.getInstance().showDialogRequests(project.projectId()).collect { breakpointRequest ->
      withContext(Dispatchers.EDT) {
        runCatching {
          BreakpointsDialogFactory.getInstance(project).showDialogImpl(breakpointRequest.breakpointId)
        }.getOrLogException(LOG)
      }
    }
  }

  project.service<FrontendXDebuggerBreakpointsDialogListenerCoroutineScope>().cs.launch {
    BreakpointsDialogFactory.getInstance(project).subscribeToShowDialogEvents(this) { breakpointId ->
      runCatching {
        BreakpointsDialogFactory.getInstance(project).showDialogImpl(breakpointId)
      }.getOrLogException(LOG)
    }
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXDebuggerBreakpointsDialogListenerCoroutineScope(val cs: CoroutineScope)