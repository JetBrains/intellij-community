// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.assertions.Assertions.assertThatThrownBy
import com.intellij.util.TimeoutUtil.sleep
import org.junit.Test
import java.awt.EventQueue.invokeLater
import java.awt.EventQueue.isDispatchThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AsyncPromiseStateTest {
  internal enum class State {
    RESOLVE, REJECT, ERROR
  }

  @Test
  fun resolveNow() {
    val promise = promise(State.RESOLVE, When.NOW)
    assertThat(promise.blockingGet(100)).isEqualTo("resolved")
  }

  @Test
  fun resolveAfterHandlerSet() {
    val promise = promise(State.RESOLVE, When.AFTER)
    assertThat(promise.blockingGet(100)).isEqualTo("resolved")
  }

  @Test
  fun resolveBeforeHandlerSet() {
    val promise = promise(State.RESOLVE, When.BEFORE)
    assertThat(promise.blockingGet(100)).isEqualTo("resolved")
  }

  @Test
  fun rejectNow() {
    val promise = promise(State.REJECT, When.NOW)
    try {
      assertThat(promise.blockingGet(100)).isNull()
    }
    catch (exception: Exception) {
      if (!isMessageError(exception)) {
        throw exception
      }
    }
  }

  @Test
  fun rejectAfterHandlerSet() {
    val promise = promise(State.REJECT, When.AFTER)
    try {
      assertThat(promise.blockingGet(100)).isNull()
    }
    catch (exception: Exception) {
      if (!isMessageError(exception)) throw exception
    }
  }

  @Test
  fun rejectBeforeHandlerSet() {
    val promise = promise(State.REJECT, When.BEFORE)
    try {
      assertThat(promise.blockingGet(100)).isNull()
    }
    catch (exception: Exception) {
      if (!isMessageError(exception)) {
        throw exception
      }
    }
  }

  @Test
  fun errorNow() {
    val promise = promise(State.ERROR, When.NOW)
    assertThatThrownBy {
      assertThat(promise.blockingGet(100)).isNull()
    }.hasCauseExactlyInstanceOf(CheckedException::class.java)
  }

  @Test
  fun errorAfterHandlerSet() {
    val promise = promise(State.ERROR, When.AFTER)
    assertThatThrownBy {
      promise.blockingGet(100)
    }.hasCauseExactlyInstanceOf(CheckedException::class.java)
  }

  @Test
  fun errorBeforeHandlerSet() {
    val promise = promise(State.ERROR, When.BEFORE)
    assertThatThrownBy {
      promise.blockingGet(100)
    }.hasCauseExactlyInstanceOf(CheckedException::class.java)
  }
}

private const val PRINT = false

private class CheckedException : Exception()

private fun log(message: String) {
  if (PRINT) {
    println(message)
  }
}

private fun promise(state: AsyncPromiseStateTest.State, `when`: When): AsyncPromise<String> {
  assert(!isDispatchThread())

  val latch = CountDownLatch(1)
  val promise = AsyncPromise<String>()
  val task = {
    try {
      sleep(10)
      when (state) {
        AsyncPromiseStateTest.State.RESOLVE -> {
          log("resolve promise")
          promise.setResult("resolved")
        }
        AsyncPromiseStateTest.State.REJECT -> {
          log("reject promise")
          promise.setError("rejected")
        }
        AsyncPromiseStateTest.State.ERROR -> {
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