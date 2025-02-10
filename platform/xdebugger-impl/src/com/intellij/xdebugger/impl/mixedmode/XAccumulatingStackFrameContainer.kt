// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

/**
 * Special frames container that can be used when you need to asynchronously wait for the completion of a frame computation
 */
@ApiStatus.Internal
class XAccumulatingStackFrameContainer : XExecutionStack.XStackFrameContainer {
  private var delegateErrorContainer: XExecutionStack.XStackFrameContainer? = null
  private val myFramesDeferred = CompletableDeferred<List<XStackFrame>>()
  private val mutableFrames = mutableListOf<XStackFrame>()
  val frames: Deferred<List<XStackFrame>> = myFramesDeferred

  override fun addStackFrames(stackFrames: List<XStackFrame?>, last: Boolean) {
    mutableFrames.addAll(stackFrames.filterNotNull())
    if (last)
      myFramesDeferred.complete(mutableFrames)
  }

  override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
    delegateErrorContainer?.errorOccurred(errorMessage)
    myFramesDeferred.completeExceptionally(Exception(errorMessage))
  }

  fun notifyOnError(container: XExecutionStack.XStackFrameContainer) {
    delegateErrorContainer = container
  }
}