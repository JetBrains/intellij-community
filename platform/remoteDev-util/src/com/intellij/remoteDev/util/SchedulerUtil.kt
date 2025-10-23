package com.intellij.remoteDev.util

import com.jetbrains.rd.util.reactive.IScheduler
import org.jetbrains.concurrency.AsyncPromise

fun <T> IScheduler.executeSync(ms: Int? = null, logErrors: Boolean = true, action: () -> T?): T? {
  class CustomAsyncPromise<T> : AsyncPromise<T>() {
    override fun shouldLogErrors(): Boolean = logErrors
  }

  if (isActive) {
    return action()
  }

  val promise = CustomAsyncPromise<T>()

  queue {
    try {
      promise.setResult(action())
    }
    catch (e: Throwable) {
      promise.setError(e)
    }
  }

  return when (ms) {
    null -> promise.get()
    else -> promise.blockingGet(ms)
  }
}

fun <T> IScheduler.executeSyncNonNullable(ms: Int? = null, logErrors: Boolean = true, action: () -> T): T =
  executeSync(ms, logErrors, action) ?: error("Expected non null result")
