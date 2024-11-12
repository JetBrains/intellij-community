// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

interface XMixedModeLowLevelDebugProcess {
  suspend fun isStopEventOnMainThread() : Boolean
  suspend fun continueAllThreads(exceptEventThread: Boolean)
  suspend fun pauseMixedModeSessionUnBlockMainThread(triggerBlockedByManagedDebuggerThreadSpin : (suspend () -> Unit))
  suspend fun pauseMixedModeSession()
}