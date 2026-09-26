// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.ShowFramesRequest
import com.intellij.platform.debugger.impl.rpc.ShowSessionTabRequest
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.sendToClient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ShowSessionTabUtils {
  val SHOW_SESSION_TAB_TOPIC: ProjectRemoteTopic<ShowSessionTabRequest> = ProjectRemoteTopic("xdebugger.show.session.tab", ShowSessionTabRequest.serializer())

  @JvmStatic
  fun showFrames(project: Project, sessionId: XDebugSessionId) {
    SHOW_SESSION_TAB_TOPIC.sendToClient(project, ShowFramesRequest(sessionId))
  }
}
