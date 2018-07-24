// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.AppUIExecutor
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.async
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable
import kotlin.coroutines.experimental.CoroutineContext

/**
 * @author eldar
 */
interface AppUIExecutorEx : AppUIExecutor {

  override fun execute(command: Runnable) {
    submit(command)
  }

  override fun submit(task: Runnable): CancellablePromise<*> {
    return submit<Any> {
      task.run()
      null
    }
  }

  override fun <T> submit(task: Callable<T>): CancellablePromise<T> {
    val deferred = runAsync { task.call() }
    return AsyncPromise<T>().apply {
      onError { cause -> deferred.cancel(cause) }
      deferred.invokeOnCompletion {
        try {
          val result = deferred.getCompleted()
          setResult(result)
        }
        catch (e: Throwable) {
          setError(e)
        }
      }
    }
  }

  suspend fun <T> runCoroutine(block: suspend () -> T): T

  fun inUndoTransparentAction(): AppUIExecutor
  fun inWriteAction(): AppUIExecutor
}

fun AppUIExecutor.inUndoTransparentAction() =
  (this as AppUIExecutorEx).inUndoTransparentAction()
fun AppUIExecutor.inWriteAction() =
  (this as AppUIExecutorEx).inWriteAction()

suspend fun <T> AppUIExecutor.runCoroutine(block: suspend () -> T): T =
  (this as AppUIExecutorEx).runCoroutine(block)

fun <T> AppUIExecutor.runAsync(context: CoroutineContext = Unconfined,
                               parent: Job? = null,
                               block: suspend () -> T): Deferred<T> =
  async(context, parent = parent) {
    runCoroutine(block)
  }
