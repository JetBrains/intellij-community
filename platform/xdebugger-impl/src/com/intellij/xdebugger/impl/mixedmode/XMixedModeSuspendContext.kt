// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XExecutionStackWithNativeThreadId
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.frame.nativeThreadId
import com.intellij.xdebugger.impl.util.adviseOnFrameChanged
import com.intellij.xdebugger.mixedMode.MixedModeFramesBuilder
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

private val logger = logger<XMixedModeSuspendContext>()

class XMixedModeSuspendContext(
  val session: XDebugSession,
  val lowLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugSuspendContext: XSuspendContext,
  val highLevelDebugProcess: XMixedModeHighLevelDebugProcess,
  val coroutineScope: CoroutineScope,
) : XSuspendContext() {

  private val stacksMap = ConcurrentHashMap<Long, XMixedModeExecutionStack>()
  private val isStacksComputed = CompletableDeferred<Boolean>()

  init {
    session.adviseOnFrameChanged { stack, _ ->
      // we need to track when frame is changed to show the correct thread after rebuildAllViews
      setActiveThreadId(stack.nativeThreadId)
    }
  }

  private var activeThreadId: Long? = null
  private val activeExecutionStackBasedOnDebugProcesses by lazy { createActiveExecutionStack() }

  override fun getActiveExecutionStack(): XExecutionStack? {
    val currentThreadId = activeThreadId
    return (if (currentThreadId == null) /*thread wasn't changed*/ activeExecutionStackBasedOnDebugProcesses else stacksMap[currentThreadId])
      ?.also { logger.info("Active execution stack ${it.topFrame}") }
  }

  override fun getExecutionStacks(): Array<out XExecutionStack?> {
    return super.getExecutionStacks()
  }

  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    coroutineScope.launch(Dispatchers.Default) {
      try {
        computeExecutionStacksInternal(container)
      }
      catch (t: Throwable) {
        if (!isStacksComputed.isCompleted)
          isStacksComputed.completeExceptionally(t)

        if (t !is CancellationException)
          throw t
      }
    }
  }

  private suspend fun computeExecutionStacksInternal(container: XExecutionStackContainer) {
    activeExecutionStackBasedOnDebugProcesses?.computedFramesMap?.await()

    val acc = MyAccumulatingContainer()

    highLevelDebugSuspendContext.computeExecutionStacks(acc)

    val highLevelStacks = measureTimedValue { acc.frames.await() }.also { logger.info("High level stacks loaded in ${it.duration}") }.value
    val threadIdToHighLevelStackMap = highLevelStacks.associateBy { stack -> stack.nativeThreadId }

    val combinedContainer = MyMixedModeCombinedContainer(
      activeExecutionStackBasedOnDebugProcesses,
      session,
      threadIdToHighLevelStackMap,
      container,
      highLevelDebugProcess.getFramesMatcher(),
      coroutineScope,
      this@XMixedModeSuspendContext)

    lowLevelDebugSuspendContext.computeExecutionStacks(combinedContainer)
  }

  private fun createActiveExecutionStack(): XMixedModeExecutionStack? {
    val lowLevelExecutionStack = lowLevelDebugSuspendContext.activeExecutionStack
    if (lowLevelExecutionStack == null) {
      return null
    }

    val threadId = lowLevelExecutionStack.nativeThreadId
    return XMixedModeExecutionStack(
      session,
      lowLevelExecutionStack,
      highLevelDebugSuspendContext.activeExecutionStack,
      highLevelDebugProcess.getFramesMatcher(),
      coroutineScope)
      .also {
        stacksMap[threadId] = it
      }
  }

  fun getComputeStacksDeferred(): Deferred<ConcurrentHashMap<Long, XMixedModeExecutionStack>> {
    val stacksMap = CompletableDeferred<ConcurrentHashMap<Long, XMixedModeExecutionStack>>()
    coroutineScope.async(Dispatchers.Default) {
      isStacksComputed.await()
      stacksMap.complete(this@XMixedModeSuspendContext.stacksMap)
    }

    return stacksMap
  }

  fun setActiveThreadId(threadId: Long) {
    activeThreadId = threadId
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
      frames.completeExceptionally(Exception(errorMessage))
    }
  }

  private class MyMixedModeCombinedContainer(
    private val activeExecutionStack: XMixedModeExecutionStack?,
    val session: XDebugSession,
    val highLevelExecutionStacks: Map<Long, XExecutionStack>,
    val resultContainer: XExecutionStackContainer,
    val mixedModeFramesMatcher: MixedModeFramesBuilder,
    val coroutineScope: CoroutineScope,
    val suspendContext: XMixedModeSuspendContext,
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

      mixedStacks.forEach { t ->
        suspendContext.stacksMap[t.nativeThreadId] = t
      }

      suspendContext.isStacksComputed.complete(true)
      resultContainer.addExecutionStack(mixedStacks, last)
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      suspendContext.isStacksComputed.completeExceptionally(Exception(errorMessage))
    }
  }
}

fun XSuspendContext.asMixedModeSuspendContext(): XMixedModeSuspendContext = (this as XMixedModeSuspendContext)
fun XSuspendContext.mixedActiveStack(): XMixedModeExecutionStack = asMixedModeSuspendContext().activeExecutionStack as XMixedModeExecutionStack
