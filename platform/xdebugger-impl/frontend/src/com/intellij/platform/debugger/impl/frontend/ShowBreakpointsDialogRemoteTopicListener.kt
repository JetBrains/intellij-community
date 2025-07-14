// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.application.runInEdt
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.topics.RemoteTopic
import com.intellij.platform.rpc.topics.RemoteTopicListener
import com.intellij.xdebugger.impl.breakpoints.SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC
import com.intellij.xdebugger.impl.breakpoints.ShowBreakpointDialogRequest
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory

private class ShowBreakpointsDialogRemoteTopicListener : RemoteTopicListener<ShowBreakpointDialogRequest> {
  override val topic: RemoteTopic<ShowBreakpointDialogRequest> = SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC

  override fun handleEvent(event: ShowBreakpointDialogRequest) {
    val project = event.projectId.findProjectOrNull() ?: return
    runInEdt {
      BreakpointsDialogFactory.getInstance(project).showDialogImpl(event.breakpointId)
    }
  }
}