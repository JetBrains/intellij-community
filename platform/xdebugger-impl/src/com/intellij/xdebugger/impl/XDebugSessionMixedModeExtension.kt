// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.util.io.await
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessDebuggeeInForeground
import com.intellij.xdebugger.frame.XMixedModeSuspendContext
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Future

private val logger = com.intellij.openapi.diagnostic.logger<XDebugProcessDebuggeeInForeground>()

interface XMixedModeDebugProcess {
  fun pauseMixedModeSession(): Future<Void>
  suspend fun resumeAndWait(): Boolean
}

abstract class XDebugSessionMixedModeExtension(
  private val coroutineScope: CoroutineScope,
  private val high: XDebugProcess,
  private val low: XDebugProcess,
) {
  private val lowMixedModeProcess: XMixedModeDebugProcess
    get() = low as XMixedModeDebugProcess
  private val highMixedModeProcess: XMixedModeDebugProcess
    get() = high as XMixedModeDebugProcess
  val lowLevelSuspendContext: XSuspendContext? = null

  abstract fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean
  abstract fun isLowStackFrame(stackFrame: XStackFrame): Boolean

  fun pause() {
    coroutineScope.launch(Dispatchers.EDT) {
      pauseAsync()
    }
  }

  // On stop, low level debugger calls positionReached first and then the high level debugger does it
  fun positionReached(debugProcess: XSuspendContext): /*Continue with new context if not null*/XSuspendContext? {
    if (!isLowSuspendContext(debugProcess)) {
      highDebugPositionReachedDeferred?.complete(debugProcess)
      return null
    }

    val lowLevelDebugProcess = debugProcess
    lowDebugPositionReachedDeferred?.complete(lowLevelDebugProcess)
    val notNullHighLevelSuspendContext = (highDebugPositionReachedDeferred ?: return null).getCompleted()
    return XMixedModeSuspendContext(lowLevelDebugProcess, notNullHighLevelSuspendContext, high, coroutineScope)
  }

  private var lowDebugPositionReachedDeferred: CompletableDeferred<XSuspendContext>? = null
  private var highDebugPositionReachedDeferred: CompletableDeferred<XSuspendContext>? = null

  private suspend fun pauseAsync() {
    lowDebugPositionReachedDeferred = CompletableDeferred()
    highDebugPositionReachedDeferred = CompletableDeferred()

    highMixedModeProcess.pauseMixedModeSession().await().also { logger.info("High level process has been stopped") }
    highDebugPositionReachedDeferred!!.await().also { logger.info("High level stopped reached a position") }

    // pausing low level session
    // but some threads can be resumed to let high level debugger work
    lowMixedModeProcess.pauseMixedModeSession().await().also { logger.info("Low level process has been stopped") }

    lowDebugPositionReachedDeferred!!.await().also { logger.info("Low level stopped reached a position") }

    lowDebugPositionReachedDeferred = null
    highDebugPositionReachedDeferred = null
  }

  fun resume() {
    coroutineScope.launch(Dispatchers.EDT) {
      lowMixedModeProcess.resumeAndWait()

      highMixedModeProcess.resumeAndWait()
    }
  }

  fun stepInto(suspendContext: XSuspendContext) {
    if (isLowSuspendContext(suspendContext)) {
      this.low.startStepInto(suspendContext)
    }
    else
      TODO("not yet supported")
  }
}


class MonoXDebugSessionMixedModeExtension(coroutineScope: CoroutineScope, high: XDebugProcess, low: XDebugProcess)
  : XDebugSessionMixedModeExtension(coroutineScope, high, low) {
  override fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }

  override fun isLowStackFrame(stackFrame: XStackFrame): Boolean {
    return stackFrame.javaClass.name.contains("Cidr")
  }
}