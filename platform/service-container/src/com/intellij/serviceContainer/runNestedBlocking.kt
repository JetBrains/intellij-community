// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package com.intellij.serviceContainer

import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.prepareThreadContext
import com.intellij.openapi.progress.readActionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.EventLoopImplBase
import kotlinx.coroutines.runBlocking

/**
 * [runBlocking] which does not process events from outer [runBlocking]
 */
@Suppress("RAW_RUN_BLOCKING")
internal fun <X> runNestedBlocking(action: suspend CoroutineScope.() -> X): X {
  return prepareThreadContext { ctx ->
    try {
      val nestedLoop = NestedBlockingEventLoop(Thread.currentThread())
      runBlocking(ctx + readActionContext() + nestedLoop, action)
    }
    catch (ce: CancellationException) {
      throw CeProcessCanceledException(ce)
    }
  }
}

private class NestedBlockingEventLoop(override val thread: Thread) : EventLoopImplBase() {

  override fun shouldBeProcessedFromContext(): Boolean {
    return true
  }
}
