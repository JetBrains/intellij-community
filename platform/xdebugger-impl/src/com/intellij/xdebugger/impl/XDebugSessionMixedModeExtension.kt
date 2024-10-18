// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.util.io.await
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XMixedModeSuspendContext
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Future

interface XMixedModeDebugProcess {
  fun pauseMixedModeSession(): Future<Void>
  suspend fun resumeAndWait() : Boolean
}

abstract class XDebugSessionMixedModeExtension(
  private val coroutineScope: CoroutineScope,
  private val high: XDebugProcess,
  private val low: XDebugProcess,
) {

  val lowMixedModeProcess: XMixedModeDebugProcess
    get() = low as XMixedModeDebugProcess
  val highMixedModeProcess: XMixedModeDebugProcess
    get() = high as XMixedModeDebugProcess

  abstract fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean

  fun pause() {
    coroutineScope.launch(Dispatchers.EDT) {
      pauseAsync()
    }
  }

  // On stop, low level debugger calls positionReached first and then the high level debugger does it
  fun positionReached(debugProcess: XSuspendContext): /*Continue with new context if not null*/XSuspendContext? {
    if (isLowSuspendContext(debugProcess)) {
      lowDebugPositionReachedDeferred?.complete(debugProcess)
      return null
    }

    val highLevelDebugProcess = debugProcess
    highDebugPositionReachedDeferred?.complete(highLevelDebugProcess)

    val notNullLowLevelSuspendContext = (lowDebugPositionReachedDeferred ?: return null).getCompleted()
    return XMixedModeSuspendContext(notNullLowLevelSuspendContext, highLevelDebugProcess, high, coroutineScope)
  }

  private var lowDebugPositionReachedDeferred: CompletableDeferred<XSuspendContext>? = null
  private var highDebugPositionReachedDeferred: CompletableDeferred<XSuspendContext>? = null

  suspend fun pauseAsync() {
    lowDebugPositionReachedDeferred = CompletableDeferred()
    highDebugPositionReachedDeferred = CompletableDeferred()

    // pausing low level session
    // but some threads can be resumed to let high level debugger work
    lowMixedModeProcess.pauseMixedModeSession().await()

    lowDebugPositionReachedDeferred!!.await()

    highMixedModeProcess.pauseMixedModeSession().await()

    highDebugPositionReachedDeferred!!.await()

    lowDebugPositionReachedDeferred = null
    highDebugPositionReachedDeferred = null
  }

  fun resume() {
    coroutineScope.launch(Dispatchers.EDT) {
      highMixedModeProcess.resumeAndWait()
      lowMixedModeProcess.resumeAndWait()
    }
  }
}


class MonoXDebugSessionMixedModeExtension(coroutineScope: CoroutineScope, high: XDebugProcess, low: XDebugProcess)
  : XDebugSessionMixedModeExtension(coroutineScope, high, low) {
  override fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }
}