// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

/**
 * This extension is primarily used by MixedModeProcessTransitionStateMachine to coordinate a low-level debugger in handling user actions
 *  * (like, step or resume)
 *
 * Mixed mode debugging can be used when a process is debugged with two different debuggers.
 * For example, a Java application can be debugged using both a C++ debugger and a Java debugger.
 * Similarly, Python or .NET applications can be debugged using a C++ debugger,
 * and Blazor applications can be debugged using both a .NET debugger and a JavaScript debugger.
 * But a low-level debugger is used when code execution dives into the low-level code (for example, when we call a native method from a managed app).
 * A low-level debug process is related to a debugger which provides a low-level view of a process (like C++ or JS in Blazor apps)
 */
@ApiStatus.Internal
interface XMixedModeLowLevelDebugProcessExtension : XMixedModeDebugProcessExtension {
  val mixedStackBuilder: MixedModeStackBuilder

  suspend fun continueAllThreads(exceptThreads: Set<Long>, silent : Boolean)

  suspend fun handleBreakpointDuringStep()

  fun pauseMixedModeSession(stopEventThreadId: Long)

  suspend fun startMixedStepInto(steppingThreadId: Long, ctx: XSuspendContext): Int

  suspend fun finishMixedStepInto()

  fun lowToHighTransitionDuringLastStepHappened() : Boolean

  suspend fun beforeStep(mixedSuspendContext: XMixedModeSuspendContextBase)

  fun belongsToMe(context: XSuspendContext) : Boolean
}