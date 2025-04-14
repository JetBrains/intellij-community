// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.mixedMode.XExecutionStackWithNativeThreadId
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.mixedMode.nativeThreadId
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx
import com.intellij.xdebugger.impl.util.notifyOnFrameChanged
import com.intellij.xdebugger.mixedMode.MixedModeStackBuilder
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import kotlin.time.measureTimedValue

private val logger = logger<XMixedModeExecutionStack>()

/**
 * Builds a mixed stack trace by matching low- and high-level execution stacks
 */
@ApiStatus.Internal
class XMixedModeExecutionStack(
  val session: XDebugSession,
  val lowLevelExecutionStack: XExecutionStack,
  // Null if there are no managed stack frames on the stack
  val highLevelExecutionStack: XExecutionStack?,
  val framesMatcher: MixedModeStackBuilder,
  val coroutineScope: CoroutineScope,
) : XExecutionStack(lowLevelExecutionStack.displayName), XExecutionStackWithNativeThreadId {

  // TODO: If suspend context has been reset before computation of frames started, this deferred will never be completed
  // TODO: need to set it cancelled in this case to not block waiters forever
  val computedFramesMap: CompletableDeferred<Map</*low-level frame*/XStackFrame, /*high-level frame*/XStackFrame?>> = CompletableDeferred()
  private var currentFrame: XStackFrame? = topFrame

  init {
    // we need to track when the current frame is changed to show the correct thread after rebuildAllViews
    (session as XDebugSessionImpl).notifyOnFrameChanged { stack, frame ->
      if (stack !is XMixedModeExecutionStack) return@notifyOnFrameChanged
      if (stack.getNativeThreadId() != getNativeThreadId()) return@notifyOnFrameChanged

      currentFrame = frame
    }

    assert((highLevelExecutionStack == null || lowLevelExecutionStack.nativeThreadId == highLevelExecutionStack.nativeThreadId))
  }

  override fun getTopFrame(): XStackFrame? {
    // when we are stopped, the top frame is always from a low-level debugger, so no need to look for a corresponding high-level frame
    return lowLevelExecutionStack.topFrame
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    if (computedFramesMap.isCompleted) {
      container as XStackFrameContainerEx
      val combinedFrames = filterIfNeeded(computedFramesMap.getCompleted().map { /*High frame*/it.value ?: /*Low frame*/it.key })
      container.addStackFrames(combinedFrames, currentFrame, true)
      return
    }

    coroutineScope.launch(Dispatchers.Default) {
      try {
        computeStackFramesInternal(firstFrameIndex, container)
      }
      catch (t: Throwable) {
        ensureActive()
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

    val isLowLevelFrameActive = session.lowLevelMixedModeExtensionOrThrow.belongsToMe(frame)
    return if (isLowLevelFrameActive)
      lowLevelExecutionStack.executionLineIconRenderer
    else
      highLevelExecutionStack?.executionLineIconRenderer
  }

  private suspend fun computeStackFramesInternal(firstFrameIndex: Int, container: XStackFrameContainer) {
    logger.info("Preparation for frame computation completed")

    val lowLevelAcc = XAccumulatingStackFrameContainer()
    lowLevelExecutionStack.computeStackFrames(firstFrameIndex, lowLevelAcc)
    val lowLevelFrames = measureTimedValue { lowLevelAcc.frames.await() }.also { logger.info("Low level frames loaded in ${it.duration}") }.value
    logger.info("Low level frames obtained")

    val highLevelAcc = XAccumulatingStackFrameContainer()
    if (highLevelExecutionStack == null) {
      logger.trace("No high level stack, adding low level frames")
      container.addStackFrames(filterIfNeeded(lowLevelFrames), true)
      computedFramesMap.complete(lowLevelFrames.associateWith { null })
    }
    else {
      highLevelExecutionStack.computeStackFrames(firstFrameIndex, highLevelAcc)
      val highLevelFrames = measureTimedValue { highLevelAcc.frames.await() }.also { logger.info("High level frames loaded in ${it.duration}") }.value
      logger.info("High level frames obtained")

      val mixFramesResult = logger.runCatching {
        measureTimedValue { framesMatcher.buildMixedStack(lowLevelExecutionStack, lowLevelFrames, highLevelFrames) }
          .also { logger.info("Mixed stack built in ${it.duration}") }.value
      }

      if (mixFramesResult.isFailure) {
        if (mixFramesResult.exceptionOrNull() !is CancellationException)
          logger.error("Failed to build mixed stack. Will use low level frames only", mixFramesResult.exceptionOrNull())

        container.addStackFrames(filterIfNeeded(lowLevelFrames), true)
        computedFramesMap.complete(lowLevelFrames.associateWith { null })
      }
      else {
        val builtResult = mixFramesResult.getOrThrow()
        container as XStackFrameContainerEx

        val combinedFrames = builtResult.lowLevelToHighLevelFrameMap.map { /*High frame*/it.value ?: /*Low frame*/it.key }
        val filterIfNeededCombinedFrames = filterIfNeeded(combinedFrames)
        container.addStackFrames(filterIfNeededCombinedFrames, builtResult.highestHighLevelFrame, true)
        computedFramesMap.complete(builtResult.lowLevelToHighLevelFrameMap)
      }
    }
  }

  // In mixed mode we can't filter low-level frames as a part of low-level execution stack logic
  // We need all the low-level frames to match them with managed ones
  private fun filterIfNeeded(frames: List<XStackFrame>): List<XStackFrame> {
    return if (XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowLibraryStackFrames)
      frames
    else
      framesMatcher.filterFrames(frames)
  }
}
