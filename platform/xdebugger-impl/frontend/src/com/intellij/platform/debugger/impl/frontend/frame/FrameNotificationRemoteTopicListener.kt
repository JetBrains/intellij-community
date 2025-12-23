// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.FrameNotificationRequest
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.xdebugger.impl.frame.FrameNotificationUtils
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy

internal class FrameNotificationRemoteTopicListener : ProjectRemoteTopicListener<FrameNotificationRequest> {
  override val topic: ProjectRemoteTopic<FrameNotificationRequest> = FrameNotificationUtils.FRAME_NOTIFICATION_REMOTE_TOPIC

  override fun handleEvent(project: Project, event: FrameNotificationRequest) {
    runInEdt {
      val session = event.sessionId?.let { XDebugManagerProxy.getInstance().findSessionProxy(project, it) }
      FrameNotificationUtils.showNotificationImpl(project, session, event.content)
    }
  }
}
