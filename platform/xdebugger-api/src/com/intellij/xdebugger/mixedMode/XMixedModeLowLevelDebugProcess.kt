// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

interface XMixedModeLowLevelDebugProcess : XMixedModeDebugProcess {
  suspend fun continueAllThreads(exceptEventThread: Boolean)
  suspend fun continueHighDebuggerServiceThreads()

  /**
   * High-level debugger may hold the threads on a kernel sync primitive when the debugger session is stopped
   * If a thread is blocked in the kernel, LLDB can't evaluate.
   * In this function we try to unblock a thread to leave the kernel code and stop in the callee
   *
   * NOTE: If the thread is not stopped by managed debugger in the described way, we'll do nothing, the thread will stay blocked in the kernel code
   **/
  suspend fun pauseMixedModeSessionUnBlockStopEventThread(stopEventThreadId: Long, triggerBlockedByManagedDebuggerThreadSpin: (suspend () -> Unit))

  fun pauseMixedModeSession(stopEventThreadId: Long)
  suspend fun findAndSetBreakpointInNativeFunction(steppingThreadId: Long, trigger: suspend (() -> Unit)): Int
  suspend fun removeTempBreakpoint(brId: Int)
  suspend fun prepareThreadBeforeFramesComputation(triggerBringingManagedThreadsToUnBlockedState: suspend () -> Unit, threadId: Long)
}