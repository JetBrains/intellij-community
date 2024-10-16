package com.intellij.xdebugger.frame

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface MixedModeFramesMatcher {
  fun matchWithHighLevelFrame(lowLevelFrame: XStackFrame): XStackFrame?
}

class XMixedModeExecutionStack(
  val lowLevelExecutionStack: XExecutionStack?,
  val highLevelExecutionStack: XExecutionStack?,
  val framesMatcher: MixedModeFramesMatcher,
  val coroutineScope: CoroutineScope,
) : XExecutionStack(lowLevelExecutionStack?.displayName ?: "<empty>") {

  override fun getTopFrame(): XStackFrame? {
    return lowLevelExecutionStack?.topFrame?.let { framesMatcher.matchWithHighLevelFrame(it) }
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
    container ?: return
    lowLevelExecutionStack ?: return

    coroutineScope.launch(Dispatchers.Default) {
      val acc = MyAccumulatingContainer()
      withContext(Dispatchers.EDT) {
        lowLevelExecutionStack.computeStackFrames(firstFrameIndex, acc)
      }

      val lowLevelFrames = acc.frames.await()

      val combinedFrames = lowLevelFrames.map { framesMatcher.matchWithHighLevelFrame(it) ?: it }
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