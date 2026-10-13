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
  doDispatchAllEventsInIdeEventQueue(null)
}

/**
 * Dispatches pending events until the IDE event queue is observed empty or the absolute [deadlineNs] (nanoseconds) is reached.
 * Use this overload when the caller must retain control of its own timeout while events keep replenishing the queue.
 * @return false if deadlineNs is breached while not all available events were dispatched
 */
@RequiresEdt
fun dispatchAllEventsInIdeEventQueue(deadlineNs: Long): Boolean {
  return doDispatchAllEventsInIdeEventQueue(deadlineNs)
}

/**
 * Keeps write-intent release and event-drain mechanics shared by bounded and unbounded dispatch
 * @return false if deadlineNs is breached while not all available  events were dispatched
 */
private fun doDispatchAllEventsInIdeEventQueue(deadlineNs: Long?): Boolean {
  ThreadingAssertions.assertEventDispatchThread()

  var timedOut = false
  releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
    while (true) {
      if(deadlineNs != null && System.nanoTime() >= deadlineNs){
        timedOut = true
        break
      }
      if (dispatchNextEventIfAny() == null) {
        break
      }
    }
  }
  return !timedOut
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
