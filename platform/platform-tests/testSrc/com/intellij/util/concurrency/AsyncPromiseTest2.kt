// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.TimeoutUtil.sleep
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.InternalPromiseUtil
import org.junit.Test
import java.awt.EventQueue.invokeLater
import java.awt.EventQueue.isDispatchThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val PRINT = false

private class CheckedException : Exception()

private fun isMessageError(exception: Exception): Boolean {
  return exception is InternalPromiseUtil.MessageError
}

private fun log(message: String) {
  if (PRINT) println(message)
}

private fun promise(state: AsyncPromiseTest2.State, `when`: When): AsyncPromise<String> {
  assert(!isDispatchThread())

  val latch = CountDownLatch(1)
  val promise = AsyncPromise<String>()
  val task = {
    try {
      sleep(10)
      when (state) {
        AsyncPromiseTest2.State.RESOLVE -> {
          log("resolve promise")
          promise.setResult("resolved")
        }
        AsyncPromiseTest2.State.REJECT -> {
          log("reject promise")
          promise.setError("rejected")
        }
        AsyncPromiseTest2.State.ERROR -> {
          log("notify promise about error to preserve a cause")
          promise.onError { /* add empty error handler to ensure that promise will not call LOG.error */ }
          promise.setError(CheckedException())
        }
      }
      latch.countDown()
    }
    catch (throwable: Throwable) {
      log("unexpected error that breaks current task")
      throwable.printStackTrace()
    }
  }

  when (`when`) {
    When.NOW -> {
      log("resolve promise immediately")
      task()
    }
    When.AFTER -> {
      log("resolve promise on another thread")
      invokeLater(task)
    }
    When.BEFORE -> {
      log("resolve promise on another thread before handler is set")
      invokeLater(task)
      sleep(50)
    }
  }

  log("add processing handlers")
  promise.onProcessed { log("promise is processed") }
  try {
    log("wait for task completion")
    latch.await(100, TimeUnit.MILLISECONDS)
    if (0L == latch.count) {
      return promise
    }

    throw AssertionError("task is not completed")
  }
  catch (exception: InterruptedException) {
    throw AssertionError("task is interrupted", exception)
  }
}

private enum class When {
  NOW, AFTER, BEFORE
}

class AsyncPromiseTest2 {
  internal enum class State {
    RESOLVE, REJECT, ERROR
  }

  @Test
  fun testResolveNow() {
    val promise = promise(State.RESOLVE, When.NOW)
    assert("resolved" == promise.blockingGet(100))
  }

  @Test
  fun testResolveAfterHandlerSet() {
    val promise = promise(State.RESOLVE, When.AFTER)
    assert("resolved" == promise.blockingGet(100))
  }

  @Test
  fun testResolveBeforeHandlerSet() {
    val promise = promise(State.RESOLVE, When.BEFORE)
    assert("resolved" == promise.blockingGet(100))
  }

  @Test
  fun testRejectNow() {
    val promise = promise(State.REJECT, When.NOW)
    try {
      assert(null == promise.blockingGet(100))
    }
    catch (exception: Exception) {
      if (!isMessageError(exception)) throw exception
    }
  }

  @Test
  fun testRejectAfterHandlerSet() {
    val promise = promise(State.REJECT, When.AFTER)
    try {
      assert(null == promise.blockingGet(100))
    }
    catch (exception: Exception) {
      if (!isMessageError(exception)) throw exception
    }
  }

  @Test
  fun testRejectBeforeHandlerSet() {
    val promise = promise(State.REJECT, When.BEFORE)
    try {
      assert(null == promise.blockingGet(100))
    }
    catch (exception: Exception) {
      if (!isMessageError(exception)) throw exception
    }
  }

  @Test
  fun testErrorNow() {
    val promise = promise(State.ERROR, When.NOW)
    try {
      assertThat(promise.blockingGet(100)).isNull()
    }
    catch (ignored: ExecutionException) {
    }
  }

  @Test
  fun testErrorAfterHandlerSet() {
    val promise = promise(State.ERROR, When.AFTER)
    try {
      assertThat(promise.blockingGet(100)).isNull()
    }
    catch (ignored: ExecutionException) {
    }
  }

  @Test
  fun testErrorBeforeHandlerSet() {
    val promise = promise(State.ERROR, When.BEFORE)
    try {
      assertThat(promise.blockingGet(100)).isNull()
    }
    catch (ignored: ExecutionException) {
    }
  }
}
