// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XExecutionStackWithNativeThreadId
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.nativeThreadId
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx
import com.intellij.xdebugger.mixedMode.MixedModeFramesBuilder
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
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

  // TODO: If suspend context has been reset before computation of frames started, this deferred will never be completed
  // TODO: need to set it cancelled in this case to not block waiters forever
  val computedFramesMap: CompletableDeferred<Map</*low level frame*/XStackFrame, /*high level frame*/XStackFrame?>> = CompletableDeferred()

  init {
    assert((highLevelExecutionStack == null || lowLevelExecutionStack.nativeThreadId == highLevelExecutionStack.nativeThreadId))
  }

  override fun getTopFrame(): XStackFrame? {
    // when we are stopped the top frame is always from a low-level debugger, so no need to look for a corresponding high level frame
    return lowLevelExecutionStack.topFrame
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    coroutineScope.launch(Dispatchers.Default) {
      try {
        computeStackFramesInternal(firstFrameIndex, container)
      }
      catch (t: Throwable) {
        if (!computedFramesMap.isCompleted)
          computedFramesMap.completeExceptionally(Exception("Failed to compute stack frames", t))

        if (t !is CancellationException)
          throw t
      }
    }
  }

  override fun getNativeThreadId(): Long = lowLevelExecutionStack.nativeThreadId

  override fun getExecutionLineIconRenderer(): GutterIconRenderer? {
    val frame = session.currentStackFrame ?: return super.getExecutionLineIconRenderer() // We don't need to render icon on a null frame

    val isLowLevelFrameActive = (session.getDebugProcess(true) as XMixedModeLowLevelDebugProcess).belongsToMe(frame)
    return if (isLowLevelFrameActive)
      lowLevelExecutionStack.executionLineIconRenderer
    else
      highLevelExecutionStack?.executionLineIconRenderer
  }

  suspend fun computeStackFramesInternal(firstFrameIndex: Int, container: XStackFrameContainer) {
      logger.info("Preparation for frame computation completed")

      val lowLevelAcc = MyAccumulatingContainer()
      lowLevelExecutionStack.computeStackFrames(firstFrameIndex, lowLevelAcc)
      val lowLevelFrames = measureTimedValue { lowLevelAcc.frames.await() }.also { logger.info("Low level frames loaded in ${it.duration}") }.value
      logger.info("Low level frames obtained")

      val highLevelAcc = MyAccumulatingContainer()
      if (highLevelExecutionStack == null) {
        logger.trace("No high level stack, adding low level frames")
        container.addStackFrames(lowLevelFrames, true)
        computedFramesMap.complete(lowLevelFrames.associateWith { null })
      }
      else {
        highLevelExecutionStack.computeStackFrames(firstFrameIndex, highLevelAcc)
        val highLevelFrames = measureTimedValue { highLevelAcc.frames.await() }.also { logger.info("High level frames loaded in ${it.duration}") }.value
        logger.info("High level frames obtained")

        val mixFramesResult = logger.runCatching {
          measureTimedValue {
            framesMatcher.buildMixedStack(session, lowLevelExecutionStack, lowLevelFrames, highLevelFrames)
          }.also { logger.info("Mixed stack built in ${it.duration}") }.value
        }

        if (mixFramesResult.isFailure) {
          logger.error("Failed to build mixed stack. Will use low level frames only", mixFramesResult.exceptionOrNull())
          container.addStackFrames(lowLevelFrames, true)
          computedFramesMap.complete(lowLevelFrames.associateWith { null })
        }
        else {
          val builtResult = mixFramesResult.getOrThrow()
          container as XStackFrameContainerEx

          val combinedFrames = builtResult.lowLevelToHighLevelFrameMap.map { /*High frame*/it.value ?: /*Low frame*/it.key }
          container.addStackFrames(combinedFrames, builtResult.highestHighLevelFrame, true)
          computedFramesMap.complete(builtResult.lowLevelToHighLevelFrameMap)
        }
      }
    }

  private class MyAccumulatingContainer : XStackFrameContainer {
    private val mutableFrames = mutableListOf<XStackFrame>()

    val frames: CompletableDeferred<List<XStackFrame>> = CompletableDeferred<List<XStackFrame>>()

    override fun addStackFrames(stackFrames: List<XStackFrame?>, last: Boolean) {
      mutableFrames.addAll(stackFrames.filterNotNull())
      if (last)
        frames.complete(mutableFrames)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      frames.completeExceptionally(Exception(errorMessage))
    }
  }
}

val XDebugSession.mixedModeExecutionStack: XMixedModeExecutionStack?
  get() = suspendContext?.activeExecutionStack as? XMixedModeExecutionStack

val XDebugSession.mixedModeExecutionStackOrThrow: XMixedModeExecutionStack
  get() = checkNotNull(mixedModeExecutionStack)