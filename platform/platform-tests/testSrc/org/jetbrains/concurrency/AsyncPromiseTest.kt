// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  fun rejected() {
    doHandlerTest(true)
  }

  @Test
  fun state() {
    val promise = AsyncPromise<String>()
    val count = AtomicInteger()

    val r = {
      promise.done { count.incrementAndGet() }
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
  fun `ignoreErrors`() {
    val a = resolvedPromise("foo")
    val b = rejectedPromise<String>()
    assertThat(collectResults(listOf(a, b), ignoreErrors = true).blockingGet(100, TimeUnit.MILLISECONDS)).containsExactly("foo")
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
        promise.rejected { count.incrementAndGet() }
      }
      else {
        promise.done { count.incrementAndGet() }
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

    r()
    assertThat(count.get()).isEqualTo(numThreads + 1)
  }
}