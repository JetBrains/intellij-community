// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.mixedMode.XExecutionStackWithNativeThreadId
import com.intellij.xdebugger.mixedMode.XMixedModeSuspendContextBase
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.mixedMode.nativeThreadId
import com.intellij.xdebugger.impl.util.adviseOnFrameChanged
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcessExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

private val LOG = logger<XMixedModeSuspendContext>()

/**
 * Special mixed mode suspend context that encapsulates high- and low-level suspend contexts,
 * When building execution stacks, it creates XMixedModeExecutionStack and pass high and low-level stack traces as ctor arguments
 */
@ApiStatus.Internal
class XMixedModeSuspendContext(
  val session: XDebugSession,
  lowLevelDebugSuspendContext: XSuspendContext,
  highLevelDebugSuspendContext: XSuspendContext,
  val lowLevelDebugProcess: XMixedModeLowLevelDebugProcessExtension,
  val mixedModeDebugCoroutineScope: CoroutineScope,
) : XMixedModeSuspendContextBase(lowLevelDebugSuspendContext, highLevelDebugSuspendContext) {

  private val stacksMap = ConcurrentHashMap<Long, XMixedModeExecutionStack>()
  private val isStacksComputed = CompletableDeferred<Boolean>()
  private var activeThreadId: Long? = null
  private val activeExecutionStackBasedOnDebugProcesses by lazy { createActiveExecutionStack() }

  init {
    session.adviseOnFrameChanged { stack, _ ->
      // we need to track when the current thread is changed to show the correct thread after rebuildAllViews
      setActiveThreadId(stack.nativeThreadId)
    }
  }

  override fun getActiveExecutionStack(): XExecutionStack? =
    when (val currentThreadId = activeThreadId) {
      null -> activeExecutionStackBasedOnDebugProcesses
      else -> stacksMap[currentThreadId]
    }?.also { LOG.info("Active execution stack ${it.topFrame}") }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    if (isStacksComputed.isCompleted) {
      isStacksComputed.getCompleted().also { assert(it) }
      container.addExecutionStack(stacksMap.values.toList(), true)
      return
    }

    mixedModeDebugCoroutineScope.launch(Dispatchers.Default) {
      try {
        computeExecutionStacksInternal(container)
      }
      catch (t: Throwable) {
        ensureActive()

        if (!isStacksComputed.isCompleted)
          isStacksComputed.completeExceptionally(t)

        if (t !is CancellationException)
          throw t
      }
    }
  }

  @TestOnly
  fun getComputeStacksDeferred(): Deferred<ConcurrentHashMap<Long, XMixedModeExecutionStack>> {
    val stacksMap = CompletableDeferred<ConcurrentHashMap<Long, XMixedModeExecutionStack>>()
    mixedModeDebugCoroutineScope.async(Dispatchers.Default) {
      isStacksComputed.await()
      stacksMap.complete(this@XMixedModeSuspendContext.stacksMap)
    }

    return stacksMap
  }

  fun setActiveThreadId(threadId: Long) {
    activeThreadId = threadId
  }

  private suspend fun computeExecutionStacksInternal(container: XExecutionStackContainer) {
    val acc = MyAccumulatingContainer()
    highLevelDebugSuspendContext.computeExecutionStacks(acc)

    val highLevelStacks = measureTimedValue { acc.frames.await() }.also { LOG.info("High level stacks loaded in ${it.duration}") }.value
    val threadIdToHighLevelStackMap = highLevelStacks.associateBy { stack -> stack.nativeThreadId }

    lowLevelDebugSuspendContext.computeExecutionStacks(object : XExecutionStackContainer {
      override fun addExecutionStack(executionStacks: List<XExecutionStack?>, last: Boolean) {
        val mixedStacks = executionStacks.map {
          if (it !is XExecutionStackWithNativeThreadId) error("Expected XExecutionStackWithNativeThreadId")
          val activeStack = activeExecutionStackBasedOnDebugProcesses
          if (activeStack?.lowLevelExecutionStack == it)
            activeStack
          else {
            val correspondedHighLevelStack = threadIdToHighLevelStackMap[it.getNativeThreadId()]
            XMixedModeExecutionStack(session,
                                     it,
                                     correspondedHighLevelStack,
                                     lowLevelDebugProcess.mixedStackBuilder,
                                     mixedModeDebugCoroutineScope)
          }
        }

        mixedStacks.forEach { t -> stacksMap[t.nativeThreadId] = t }
        container.addExecutionStack(mixedStacks, last)

        if (last)
          isStacksComputed.complete(true)
      }

      override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
        isStacksComputed.completeExceptionally(Exception(errorMessage))
      }
    })
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
      lowLevelDebugProcess.mixedStackBuilder,
      mixedModeDebugCoroutineScope)
      .also {
        stacksMap[threadId] = it
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
      frames.completeExceptionally(Exception(errorMessage))
    }
  }
}

internal fun XSuspendContext.asMixedModeSuspendContext(): XMixedModeSuspendContext = (this as XMixedModeSuspendContext)

@ApiStatus.Internal
fun XSuspendContext.mixedActiveStack(): XMixedModeExecutionStack = asMixedModeSuspendContext().activeExecutionStack as XMixedModeExecutionStack
