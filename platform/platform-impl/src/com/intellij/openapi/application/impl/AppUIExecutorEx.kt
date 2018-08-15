// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.AppUIExecutor
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable
import kotlin.coroutines.experimental.coroutineContext

/**
 * @author eldar
 */
interface AppUIExecutorEx : AppUIExecutor, AsyncExecution {

  override fun execute(command: Runnable) {
    // Note, that launch() is different from async() used by submit():
    //
    //   - processing of async() errors thrown by the command are deferred
    //     until the Deferred.await() is called on the result,
    //
    //   - errors thrown within launch() are not caught, and usually result in an error
    //     message with a stack trace to be logged on the corresponding thread.
    //
    launch(createJobContext()) {
      command.run()
    }
  }

  override fun submit(task: Runnable): CancellablePromise<*> {
    return submit<Any> {
      task.run()
      null
    }
  }

  override fun <T> submit(task: Callable<T>): CancellablePromise<T> {
    val deferred = async(createJobContext()) {
      task.call()
    }
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

  fun inUndoTransparentAction(): AppUIExecutor
  fun inWriteAction(): AppUIExecutor
}

fun AppUIExecutor.inUndoTransparentAction() =
  (this as AppUIExecutorEx).inUndoTransparentAction()
fun AppUIExecutor.inWriteAction() =
  (this as AppUIExecutorEx).inWriteAction()


suspend fun <T> AppUIExecutor.runCoroutine(block: suspend () -> T): T =
  withContext((this as AsyncExecution).createJobContext(coroutineContext)) {
    block()
  }
