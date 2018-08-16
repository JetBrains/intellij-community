// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.concurrency.JobScheduler
import com.intellij.testFramework.assertConcurrent
import com.intellij.testFramework.assertConcurrentPromises
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.fail

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
    val log = StringBuffer()

    class Incrementer(val descr:String) : ()->Promise<String> {
      override fun toString(): String {
        return descr
      }

      override fun invoke(): Promise<String> {
        return promise.onSuccess { count.incrementAndGet(); log.append("\n" + this + " " + System.identityHashCode(this)) }
      }
    }

    val setResulter: () -> Promise<String> = {
      promise.setResult("test")
      promise
    }

    val numThreads = 30
    val array = Array(numThreads) {
      if ((it and 1) == 0) Incrementer("handler $it") else setResulter
    }
    assertConcurrentPromises(*array)

    if (count.get() != (numThreads / 2)) {
      fail("count: "+count +" "+ log.toString()+"\n---Array:\n"+ Arrays.toString(array))
    }
    assertThat(count.get()).isEqualTo(numThreads / 2)
    assertThat(promise.get()).isEqualTo("test")

    Incrementer("extra").invoke()
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

  @Test
  fun collectResultsMustReturnArrayWithTheSameOrder() {
    val promise0 = AsyncPromise<String>()
    val promise1 = AsyncPromise<String>()
    val f0 = JobScheduler.getScheduler().schedule({ promise0.setResult("0") }, 10, TimeUnit.SECONDS)
    val f1 = JobScheduler.getScheduler().schedule({ promise1.setResult("1") }, 1, TimeUnit.SECONDS)
    val list = Arrays.asList(promise0, promise1)
    val results = list.collectResults()
    val l = results.blockingGet(1, TimeUnit.MINUTES)
    assertEquals(listOf("0", "1"), l)
    f0.get();
    f1.get();
  }

}