package com.intellij.openapi.wm.impl.welcomeScreen

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object WelcomeScreenEventCollectorDebuggerExtensions {
  //Rider-specific log events
  fun logDebuggerProcessesSearchUsed(): Unit = WelcomeScreenEventCollector.debuggerTabProcessesSearchUsed.log()
  fun logDebuggerAttached(): Unit = WelcomeScreenEventCollector.debuggerAttachUsed.log()
}