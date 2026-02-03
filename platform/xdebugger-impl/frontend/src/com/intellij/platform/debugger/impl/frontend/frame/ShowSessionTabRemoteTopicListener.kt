// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.ShowFramesRequest
import com.intellij.platform.debugger.impl.rpc.ShowSessionTabRequest
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.xdebugger.impl.frame.ShowSessionTabUtils
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.XDebugSessionTab

internal class ShowSessionTabRemoteTopicListener : ProjectRemoteTopicListener<ShowSessionTabRequest> {
  override val topic: ProjectRemoteTopic<ShowSessionTabRequest>
    get() = ShowSessionTabUtils.SHOW_SESSION_TAB_TOPIC

  override fun handleEvent(project: Project, event: ShowSessionTabRequest) {
    val session = XDebugManagerProxy.getInstance().findSessionProxy(project, event.sessionId) ?: return
    runInEdt {
      when (event) {
        is ShowFramesRequest -> XDebugSessionTab.showFramesView(session)
      }
    }
  }
}