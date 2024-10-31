package com.intellij.xdebugger.frame

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class XMixedModeExecutionStack(
  val session: XDebugSession,
  val lowLevelExecutionStack: XExecutionStack,
  val highLevelExecutionStack: XExecutionStack?,
  val framesMatcher: MixedModeFramesBuilder,
  val coroutineScope: CoroutineScope,
) : XExecutionStack(lowLevelExecutionStack.displayName) {

  override fun getTopFrame(): XStackFrame? {
    // when we are stopped the top frame is always from a low-level debugger, so no need to look for a corresponding high level frame
    return lowLevelExecutionStack.topFrame
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    coroutineScope.launch(Dispatchers.EDT) {
      val lowLevelAcc = MyAccumulatingContainer()
      lowLevelExecutionStack.computeStackFrames(firstFrameIndex, lowLevelAcc)
      val lowLevelFrames = lowLevelAcc.frames.await()

      val highLevelAcc = MyAccumulatingContainer()
      requireNotNull(highLevelExecutionStack).computeStackFrames(firstFrameIndex, highLevelAcc)
      val highLevelFrames = highLevelAcc.frames.await()

      val combinedFrames = framesMatcher.buildMixedStack(session, lowLevelFrames, highLevelFrames)
      container.addStackFrames(combinedFrames, true)
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
      TODO("Not yet implemented")
    }
  }
}