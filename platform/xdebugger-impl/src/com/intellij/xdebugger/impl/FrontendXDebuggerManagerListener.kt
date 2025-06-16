// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.util.messages.Topic
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.asProxy
import org.jetbrains.annotations.ApiStatus

/**
 * Frontend-side analogue of [com.intellij.xdebugger.XDebuggerManagerListener]
 */
@ApiStatus.Internal
interface FrontendXDebuggerManagerListener {
  fun sessionStarted(session: XDebugSessionProxy) {}
  fun sessionStopped(session: XDebugSessionProxy) {}
  fun activeSessionChanged(previousSession: XDebugSessionProxy?, currentSession: XDebugSessionProxy?) {}

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<FrontendXDebuggerManagerListener> =
      Topic("FrontendXDebuggerManager events", FrontendXDebuggerManagerListener::class.java)
  }
}

private class MonolithListenerAdapter : XDebuggerManagerListener {
  private val shouldTriggerListener get() = !XDebugSessionProxy.useFeProxy()

  override fun processStarted(debugProcess: XDebugProcess) {
    if (!shouldTriggerListener) return
    val session = debugProcess.session
    session.project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).sessionStarted(session.asProxy())
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    if (!shouldTriggerListener) return
    val session = debugProcess.session
    session.project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).sessionStopped(session.asProxy())
  }

  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    if (!shouldTriggerListener) return
    val project = previousSession?.project ?: currentSession?.project ?: return
    project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).activeSessionChanged(previousSession?.asProxy(),
                                                                                                  currentSession?.asProxy())
  }
}
