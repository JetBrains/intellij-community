package com.intellij.openapi.wm.impl.welcomeScreen

object WelcomeScreenEventCollectorDebuggerExtensions {
  //Rider-specific log events
  fun logDebuggerProcessesSearchUsed() = WelcomeScreenEventCollector.debuggerTabProcessesSearchUsed.log()
  fun logDebuggerAttached() = WelcomeScreenEventCollector.debuggerAttachUsed.log()
}