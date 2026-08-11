// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.impl.TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import java.awt.AWTEvent

/**
 * Legacy synchronous bridge to EDT. In coroutine-based tests, prefer a bounded
 * `timeoutRunBlocking` boundary and a small `withContext(Dispatchers.UI)` block. Use
 * `Dispatchers.EDT` only for operations known to require IntelliJ model or lock access.
 */
@TestOnly
fun <V> runInEdtAndGet(compute: () -> V): V {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  return EdtTestUtil.runInEdtAndGet(ThrowableComputable<V, Throwable> { compute() }, true)
}

/**
 * Legacy synchronous bridge to EDT. In coroutine-based tests, prefer a bounded
 * `timeoutRunBlocking` boundary and a small `withContext(Dispatchers.UI)` block. Use
 * `Dispatchers.EDT` only for operations known to require IntelliJ model or lock access.
 */
@TestOnly
fun <V> runInEdtAndGet(writeIntent: Boolean, compute: () -> V): V {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  return EdtTestUtil.runInEdtAndGet(ThrowableComputable<V, Throwable> { compute() }, writeIntent)
}

/**
 * Legacy synchronous bridge to EDT. In coroutine-based tests, prefer a bounded
 * `timeoutRunBlocking` boundary and a small `withContext(Dispatchers.UI)` block. Use
 * `Dispatchers.EDT` only for operations known to require IntelliJ model or lock access.
 */
@TestOnly
fun runInEdtAndWait(runnable: () -> Unit) {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  EdtTestUtil.runInEdtAndWait(ThrowableRunnable<Throwable> { runnable() }, true)
}

/**
 * Legacy synchronous bridge to EDT. In coroutine-based tests, prefer a bounded
 * `timeoutRunBlocking` boundary and a small `withContext(Dispatchers.UI)` block. Use
 * `Dispatchers.EDT` only for operations known to require IntelliJ model or lock access.
 */
@TestOnly
fun runInEdtAndWait(writeIntent: Boolean, runnable: () -> Unit) {
  @Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
  EdtTestUtil.runInEdtAndWait(ThrowableRunnable<Throwable> { runnable() }, writeIntent)
}


/**
 * Dispatch all pending events (if any) in the [com.intellij.ide.IdeEventQueue]. Should only be invoked from EDT.
 *
 *  Do not use in a new code.
 */
@RequiresEdt
fun dispatchAllEventsInIdeEventQueue() {
  ThreadingAssertions.assertEventDispatchThread()

  releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
    while (true) {
      if (dispatchNextEventIfAny() == null) {
        break
      }
    }
  }
}

/**
 * Dispatch one pending event (if any) in the [IdeEventQueue]. Should only be invoked from EDT.
 */
fun dispatchNextEventIfAny(): AWTEvent? {
  return resetThreadContext {
    ThreadingAssertions.assertEventDispatchThread()

    val eventQueue = IdeEventQueue.getInstance()
    if (eventQueue.peekEvent() == null) {
      return@resetThreadContext null
    }

    val event = eventQueue.getNextEvent()
    eventQueue.dispatchEvent(event)
    event
  }
}
