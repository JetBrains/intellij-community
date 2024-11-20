// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.*
import kotlin.time.measureTimedValue

private val logger = com.intellij.openapi.diagnostic.logger<XMixedModeSuspendContext>()

class XMixedModeSuspendContext(
  val session: XDebugSession,
  val lowLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugProcess: XMixedModeHighLevelDebugProcess,
  val coroutineScope: CoroutineScope,
) : XSuspendContext() {

  private val activeExecutionStack by lazy { createActiveExecutionStack() }

  override fun getActiveExecutionStack(): XExecutionStack? {
    return activeExecutionStack
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

      val highLevelStacks = measureTimedValue { acc.frames.await() }.also { logger.info("High level stacks loaded in ${it.duration}") }.value
      val threadIdToHighLevelStackMap = highLevelStacks.associateBy { (it as XExecutionStackWithNativeThreadId).getNativeThreadId() }

      val combinedContainer = MyMixedModeCombinedContainer(
        activeExecutionStack,
        session,
        threadIdToHighLevelStackMap,
        container,
        highLevelDebugProcess.getFramesMatcher(),
        coroutineScope)

      lowLevelDebugSuspendContext.computeExecutionStacks(combinedContainer)
    }
  }

  private fun createActiveExecutionStack(): XMixedModeExecutionStack? {
    val lowLevelExecutionStack = lowLevelDebugSuspendContext.activeExecutionStack
    if (lowLevelExecutionStack == null) {
      // If we don't have a low-level execution stack, we are not supposed to show any frames
      return null
    }

    return XMixedModeExecutionStack(
      session,
      lowLevelExecutionStack,
      highLevelDebugSuspendContext.activeExecutionStack,
      highLevelDebugProcess.getFramesMatcher(),
      coroutineScope)
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
    private val activeExecutionStack: XMixedModeExecutionStack?,
    val session: XDebugSession,
    val highLevelExecutionStacks: Map<Long, XExecutionStack>,
    val resultContainer: XExecutionStackContainer,
    val mixedModeFramesMatcher: MixedModeFramesBuilder,
    val coroutineScope: CoroutineScope,
  ) : XExecutionStackContainer {
    override fun addExecutionStack(executionStacks: List<XExecutionStack?>, last: Boolean) {
      val mixedStacks = executionStacks.filterNotNull().map {
        if (it !is XExecutionStackWithNativeThreadId) error("Expected XExecutionStackWithNativeThreadId")
        if (activeExecutionStack?.lowLevelExecutionStack == it) {
          activeExecutionStack
        }
        else {
          val correspondedHighLevelStack = highLevelExecutionStacks[it.getNativeThreadId()]
          XMixedModeExecutionStack(session, it, correspondedHighLevelStack, mixedModeFramesMatcher, coroutineScope)
        }
      }

      resultContainer.addExecutionStack(mixedStacks, last)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      TODO("Not yet implemented")
    }
  }
}

