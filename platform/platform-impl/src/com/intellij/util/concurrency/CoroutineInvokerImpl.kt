// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.progress.blockingContext
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.util.ThreeState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

internal abstract class CoroutineInvokerImpl(
  override val description: String,
  private val scope: CoroutineScope,
  protected val maxThreads: Int,
) : InvokerImpl {
  override fun dispose() {
    scope.cancel()
  }

  override fun offer(runnable: Runnable, delay: Int, promise: Promise<*>) {
    val job = scope.launch(CoroutineName(runnable.toString()), start) {
      delay(delay.toLong())
      withPermit {
        if (start == CoroutineStart.UNDISPATCHED) yield() // In case the current thread is the EDT, need to free it.
        onValidThread {
          kotlin.runCatching {
            runnable.run()
          }.getOrLogException {
            Invoker.LOG.error("$description: Task $runnable threw an unexpected exception", it)
          }
        }
      }
    }
    promise.onProcessed { job.cancel("Promise was cancelled") }
  }

  override fun run(task: Runnable, promise: AsyncPromise<*>): Boolean {
    task.run()
    return true
  }

  protected abstract val start: CoroutineStart

  protected abstract suspend fun withPermit(runnable: suspend () -> Unit)

  protected abstract suspend fun onValidThread(runnable: () -> Unit)
}

internal class EdtCoroutineInvokerImpl(
  description: String,
  scope: CoroutineScope,
) : CoroutineInvokerImpl(description, scope, maxThreads = 1) {
  override val start: CoroutineStart
    get() = CoroutineStart.UNDISPATCHED

  override suspend fun withPermit(runnable: suspend () -> Unit) {
    // For running on the EDT we can't use a semaphore because of modality.
    // Reentrant calls are possible while somewhere up the stack some code
    // is already running inside the same Invoker.
    // Instead, for ordering we rely on withContext(Dispatchers.EDT),
    // as it'll queue invocation events in the order it's called.
    runnable()
  }

  override suspend fun onValidThread(runnable: () -> Unit) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      blockingContext { // We shouldn't really be blocking the EDT, but we still need this for ProgressManager.checkCanceled.
        runnable()
      }
    }
  }
}

internal class BgtCoroutineInvokerImpl(
  description: String,
  scope: CoroutineScope,
  private val useReadAction: ThreeState,
  maxThreads: Int,
) : CoroutineInvokerImpl(description, scope, maxThreads) {
  private val semaphore = OverflowSemaphore(maxThreads, overflow = BufferOverflow.SUSPEND)

    override val start: CoroutineStart // Using UNDISPATCHED+yield with multiple "threads" lead to strange yield() delays.
    get() = if (maxThreads == 1) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT

  override suspend fun withPermit(runnable: suspend () -> Unit) {
    semaphore.withPermit {
      runnable()
    }
  }

  override suspend fun onValidThread(runnable: () -> Unit) {
    if (useReadAction == ThreeState.YES) {
      readAction {
        runnable()
      }
    }
    else {
      blockingContext {
        runnable()
      }
    }
  }
}
