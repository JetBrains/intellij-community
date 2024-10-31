// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XMixedModeSuspendContext(
  val session: XDebugSession,
  val lowLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugProcess: XDebugProcess,
  val coroutineScope: CoroutineScope,
) : XSuspendContext() {

  override fun getActiveExecutionStack(): XExecutionStack? {
    val lowLevelExecutionStack = lowLevelDebugSuspendContext.activeExecutionStack
    if (lowLevelExecutionStack == null) {
      // If we don't have a low-level execution stack, we are not supposed to show any frames
      return null
    }

    return XMixedModeExecutionStack(
      session,
      lowLevelExecutionStack,
      highLevelDebugSuspendContext.activeExecutionStack,
      highLevelDebugProcess.asHighLevel().getFramesMatcher(),
      coroutineScope)
  }

  override fun getExecutionStacks(): Array<out XExecutionStack?> {
    return super.getExecutionStacks()
  }

  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    coroutineScope.launch(Dispatchers.Default) {

      val acc = MyAccumulatingContainer()

      withContext(Dispatchers.EDT) {
        highLevelDebugSuspendContext.computeExecutionStacks(acc)
      }

      val highLevelStacks = acc.frames.await()

      val threadIdToHighLevelStackMap = highLevelStacks.associateBy { (it as XExecutionStackWithNativeThreadId).getNativeThreadId() }

      val combinedContainer = MyMixedModeCombinedContainer(
        session,
        threadIdToHighLevelStackMap,
        container,
        highLevelDebugProcess.asHighLevel().getFramesMatcher(),
        coroutineScope)

      lowLevelDebugSuspendContext.computeExecutionStacks(combinedContainer)
    }
  }

  private class MyAccumulatingContainer : XExecutionStackContainer {
    private val mutableStacks = mutableListOf<XExecutionStack>()
    val frames: CompletableDeferred<List<XExecutionStack>> = CompletableDeferred<List<XExecutionStack>>()
    override fun addExecutionStack(executionStacks: List<XExecutionStack?>, last: Boolean) {
      executionStacks.filterNotNull().forEach { mutableStacks.add(it) }

      if (last)
        frames.complete(mutableStacks)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      TODO("Not yet implemented")
    }
  }

  private class MyMixedModeCombinedContainer(
    val session: XDebugSession,
    val highLevelExecutionStacks: Map<Long, XExecutionStack>,
    val resultContainer: XExecutionStackContainer,
    val mixedModeFramesMatcher: MixedModeFramesBuilder,
    val coroutineScope: CoroutineScope,
  ) : XExecutionStackContainer {
    override fun addExecutionStack(executionStacks: List<XExecutionStack?>, last: Boolean) {
      val mixedStacks = executionStacks.filterNotNull().map {
        if (it !is XExecutionStackWithNativeThreadId) error("Expected XExecutionStackWithNativeThreadId")
        val correspondedHighLevelStack = highLevelExecutionStacks[it.getNativeThreadId()]
        XMixedModeExecutionStack(session, it, correspondedHighLevelStack, mixedModeFramesMatcher, coroutineScope)
      }

      resultContainer.addExecutionStack(mixedStacks, last)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      TODO("Not yet implemented")
    }
  }
}

private fun XDebugProcess.asHighLevel() = this as XMixedModeHighLevelDebugProcess


