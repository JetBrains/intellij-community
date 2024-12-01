// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XExecutionStackWithNativeThreadId
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.nativeThreadId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.measureTimedValue

private val logger = logger<XMixedModeExecutionStack>()

class XMixedModeExecutionStack(
  val session: XDebugSession,
  val lowLevelExecutionStack: XExecutionStack,
  // Null if there's no managed stack frames on the stack
  val highLevelExecutionStack: XExecutionStack?,
  val framesMatcher: MixedModeFramesBuilder,
  val coroutineScope: CoroutineScope,
) : XExecutionStack(lowLevelExecutionStack.displayName), XExecutionStackWithNativeThreadId {

  init {
    assert((highLevelExecutionStack == null || lowLevelExecutionStack.nativeThreadId == highLevelExecutionStack.nativeThreadId))
  }

  var computationCompleted: CompletableDeferred<Unit> = CompletableDeferred()
  var computedFrames: CompletableDeferred<List<XStackFrame>> = CompletableDeferred()

  override fun getTopFrame(): XStackFrame? {
    // when we are stopped the top frame is always from a low-level debugger, so no need to look for a corresponding high level frame
    return lowLevelExecutionStack.topFrame
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    coroutineScope.launch(Dispatchers.Default) {
      prepareThreadBeforeFrameComputation()

      val lowLevelAcc = MyAccumulatingContainer()
      lowLevelExecutionStack.computeStackFrames(firstFrameIndex, lowLevelAcc)
      val lowLevelFrames = measureTimedValue { lowLevelAcc.frames.await() }.also { logger.info("Low level frames loaded in ${it.duration}") }.value

      val highLevelAcc = MyAccumulatingContainer()
      if (highLevelExecutionStack == null) {
        logger.trace("No high level stack, adding low level frames")
        container.addStackFrames(lowLevelFrames, true)
        computedFrames.complete(lowLevelFrames)
      }
      else {
        highLevelExecutionStack.computeStackFrames(firstFrameIndex, highLevelAcc)
        val highLevelFrames = measureTimedValue { highLevelAcc.frames.await() }.also { logger.info("High level frames loaded in ${it.duration}") }.value

        val combinedFrames = logger.runCatching {
          measureTimedValue {
            framesMatcher.buildMixedStack(session, lowLevelFrames, highLevelFrames)
          }.also { logger.info("Mixed stack built in ${it.duration}") }.value
        }

        val framesToAdd = if (combinedFrames.isFailure) {
          logger.error("Failed to build mixed stack. Will use low level frames only", combinedFrames.exceptionOrNull())
          lowLevelFrames
        }
        else {
          combinedFrames.getOrThrow()
        }

        container.addStackFrames(framesToAdd, true)
        computedFrames.complete(framesToAdd)
      }
    }.invokeOnCompletion {
      computationCompleted.complete(Unit)
      if (!computedFrames.isCompleted) {
        computedFrames.completeExceptionally(Exception("computedFrames hasn't been set, possibly due to an exception"))
      }
    }
  }

  private suspend fun prepareThreadBeforeFrameComputation() {
    val threadId = lowLevelExecutionStack.nativeThreadId
    val lowLevel = session.getDebugProcess(true) as XMixedModeLowLevelDebugProcess
    val highLevel = session.getDebugProcess(false) as XMixedModeHighLevelDebugProcess
    lowLevel.prepareThreadBeforeFramesComputation({ highLevel.triggerBringingManagedThreadsToUnBlockedState() }, threadId)
  }

  override fun getNativeThreadId(): Long = lowLevelExecutionStack.nativeThreadId

  private class MyAccumulatingContainer : XStackFrameContainer {
    private val mutableFrames = mutableListOf<XStackFrame>()

    val frames: CompletableDeferred<List<XStackFrame>> = CompletableDeferred<List<XStackFrame>>()

    override fun addStackFrames(stackFrames: List<XStackFrame?>, last: Boolean) {
      mutableFrames.addAll(stackFrames.filterNotNull())
      if (last)
        frames.complete(mutableFrames)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      TODO("Not yet implemented")
    }
  }
}