// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.testFramework.assertConcurrent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class AsyncPromiseTest {
  @Test
  fun done() {
    doHandlerTest(false)
  }

  @Test
  fun cancel() {
    val promise = AsyncPromise<Boolean>()
    assertThat(promise.isCancelled).isFalse()
    assertThat(promise.cancel(true)).isTrue()
    assertThat(promise.isCancelled).isTrue()
    assertThat(promise.cancel(true)).isFalse()
    assertThat(promise.isCancelled).isTrue()
    assertThat(promise.blockingGet(1)).isNull()
  }

  @Test
  fun rejected() {
    doHandlerTest(true)
  }

  @Test
  fun state() {
    val promise = AsyncPromise<String>()
    val count = AtomicInteger()

    val r = {
      promise
        .onSuccess { count.incrementAndGet() }
    }

    val s = {
      promise.setResult("test")
    }

    val numThreads = 30
    assertConcurrent(*Array(numThreads, {
      if ((it and 1) == 0) r else s
    }))

    assertThat(count.get()).isEqualTo(numThreads / 2)
    assertThat(promise.get()).isEqualTo("test")

    r()
    assertThat(count.get()).isEqualTo((numThreads / 2) + 1)
  }

  @Test
  fun blockingGet() {
    val promise = AsyncPromise<String>()
    assertConcurrent(
      { assertThat(promise.blockingGet(1000)).isEqualTo("test") },
      {
        Thread.sleep(100)
        promise.setResult("test")
      })
  }

  @Test
  fun `get from Future`() {
    val promise = AsyncPromise<String>()
    assertConcurrent(
      { assertThat(promise.get(1000, TimeUnit.MILLISECONDS)).isEqualTo("test") },
      {
        Thread.sleep(100)
        promise.setResult("test")
      })
  }

  @Test
  fun `ignore errors`() {
    val a = resolvedPromise("foo")
    val b = rejectedPromise<String>()
    assertThat(listOf(a, b).collectResults(ignoreErrors = true).blockingGet(100, TimeUnit.MILLISECONDS)).containsExactly("foo")
  }

  @Test
  fun blockingGet2() {
    val promise = AsyncPromise<String>()
    assertConcurrent(
        { assertThatThrownBy { promise.blockingGet(100) }.isInstanceOf(TimeoutException::class.java) },
        {
          Thread.sleep(1000)
          promise.setResult("test")
        })
  }

  private fun doHandlerTest(reject: Boolean) {
    val promise = AsyncPromise<String>()
    val count = AtomicInteger()

    val r = {
      if (reject) {
        promise.onError { count.incrementAndGet() }
      }
      else {
        promise.onSuccess { count.incrementAndGet() }
      }
    }

    val numThreads = 30
    assertConcurrent(*Array(numThreads, { r }))

    if (reject) {
      promise.setError("test")
    }
    else {
      promise.setResult("test")
    }

    assertThat(count.get()).isEqualTo(numThreads)
    if (!reject) {
      assertThat(promise.get()).isEqualTo("test")
    }
    assertThat(promise.isDone).isTrue()
    assertThat(promise.isCancelled).isFalse()

    r()
    assertThat(count.get()).isEqualTo(numThreads + 1)
  }
}