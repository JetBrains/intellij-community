// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.rpc.topics.RemoteTopic
import com.intellij.xdebugger.impl.breakpoints.SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC
import com.intellij.xdebugger.impl.breakpoints.ShowBreakpointDialogRequest
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory

private class ShowBreakpointsDialogRemoteTopicListener : ProjectRemoteTopicListener<ShowBreakpointDialogRequest> {
  override val topic: ProjectRemoteTopic<ShowBreakpointDialogRequest> = SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC

  override fun handleEvent(project: Project, event: ShowBreakpointDialogRequest) {
    runInEdt {
      BreakpointsDialogFactory.getInstance(project).showDialogImpl(event.breakpointId)
    }
  }
}