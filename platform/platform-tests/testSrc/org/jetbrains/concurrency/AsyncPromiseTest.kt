/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.concurrency

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
      if (it and 1 === 0) r else s
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
        { assertThat(promise.blockingGet(100)).isEqualTo("test") },
        {
          Thread.sleep(80)
          promise.setResult("test")
        })
  }

  @Test
  fun blockingGet2() {
    val promise = AsyncPromise<String>()
    assertConcurrent(
        { assertThatThrownBy { promise.blockingGet(100) }.isInstanceOf(TimeoutException::class.java) },
        {
          Thread.sleep(200)
          promise.setResult("test")
        })
  }

  fun doHandlerTest(reject: Boolean) {
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

fun assertConcurrent(vararg runnables: () -> Any?, maxTimeoutSeconds: Int = 5) {
  val numThreads = runnables.size
  val exceptions = ContainerUtil.createLockFreeCopyOnWriteList<Throwable>()
  val threadPool = Executors.newFixedThreadPool(numThreads)
  try {
    val allExecutorThreadsReady = CountDownLatch(numThreads)
    val afterInitBlocker = CountDownLatch(1)
    val allDone = CountDownLatch(numThreads)
    for (submittedTestRunnable in runnables) {
      threadPool.submit {
        allExecutorThreadsReady.countDown()
        try {
          afterInitBlocker.await()
          submittedTestRunnable()
        }
        catch (e: Throwable) {
          exceptions.add(e)
        }
        finally {
          allDone.countDown()
        }
      }
    }

    // wait until all threads are ready
    assertThat(allExecutorThreadsReady.await((runnables.size * 10).toLong(), TimeUnit.MILLISECONDS)).isTrue()
    // start all test runners
    afterInitBlocker.countDown()
    assertThat(allDone.await(maxTimeoutSeconds.toLong(), TimeUnit.SECONDS)).isTrue()
  }
  finally {
    threadPool.shutdownNow()
  }
  CompoundRuntimeException.throwIfNotEmpty(exceptions)
}