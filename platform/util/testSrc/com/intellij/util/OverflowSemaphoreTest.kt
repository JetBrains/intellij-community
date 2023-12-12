// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.containers.headTail
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.streams.asStream

class OverflowSemaphoreTest {

  private class PreconditionsArguments : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = sequence {
      for (overflow in BufferOverflow.values()) {
        for (concurrency in arrayOf(-1, 0)) {
          yield(Arguments.of(overflow, concurrency))
        }
      }
    }.asStream()
  }

  @ParameterizedTest
  @ArgumentsSource(PreconditionsArguments::class)
  fun preconditions(overflow: BufferOverflow, concurrency: Int) {
    assertThrows<IllegalArgumentException> {
      OverflowSemaphore(concurrency, overflow)
    }
  }

  private class RethrowArguments : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = sequence {
      for (overflow in BufferOverflow.values()) {
        for (concurrency in arrayOf(1, 2)) {
          yield(Arguments.of(overflow, concurrency))
        }
      }
    }.asStream()
  }

  @ParameterizedTest
  @ArgumentsSource(RethrowArguments::class)
  fun rethrow(overflow: BufferOverflow, concurrency: Int) {
    timeoutRunBlocking {
      val t: Throwable = object : Throwable() {}
      val semaphore = OverflowSemaphore(concurrency, overflow)
      val thrown = assertThrows<Throwable> {
        semaphore.withPermit {
          throw t
        }
      }
      assertSame(t, thrown)
    }
  }

  private class ConcurrencyArguments : ArgumentsProvider {
    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = sequence {
      for (overflow in BufferOverflow.values()) {
        for (concurrency in arrayOf(1, 10, 100)) {
          yield(Arguments.of(overflow, concurrency))
        }
      }
    }.asStream()
  }

  @ParameterizedTest
  @ArgumentsSource(ConcurrencyArguments::class)
  @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
  fun concurrency(overflow: BufferOverflow, concurrency: Int) {
    val semaphore = OverflowSemaphore(concurrency, overflow)
    val counter = AtomicInteger()
    timeoutRunBlocking {
      withContext(Dispatchers.Default) {
        repeat(concurrency * kotlinx.coroutines.scheduling.CORE_POOL_SIZE * 100) {
          launch {
            semaphore.withPermit {
              val counterValue = counter.incrementAndGet()
              try {
                assertTrue(counterValue <= concurrency) {
                  "Overflow: $overflow, concurrency: $concurrency, counter: $counterValue"
                }
                yield()
              }
              finally {
                counter.getAndDecrement()
              }
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [1, 10, 100])
  fun `DROP_OLDEST cancels oldest`(concurrency: Int) {
    val semaphore = OverflowSemaphore(concurrency, overflow = BufferOverflow.DROP_OLDEST)
    timeoutRunBlocking {
      val jobs = fillQueue(semaphore, concurrency)
      semaphore.withPermit {}
      val (first, rest) = jobs.headTail()
      assertFalse(first.isActive)
      assertTrue(first.isCompleted)
      assertTrue(first.isCancelled)
      for (job in rest) {
        assertTrue(job.isActive)
        assertFalse(job.isCompleted)
        assertFalse(job.isCancelled)
        job.cancel()
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [1, 10, 100])
  fun `DROP_LATEST cancels latest`(concurrency: Int) {
    val semaphore = OverflowSemaphore(concurrency, overflow = BufferOverflow.DROP_LATEST)
    timeoutRunBlocking {
      val jobs = fillQueue(semaphore, concurrency)
      assertThrows<CancellationException> {
        semaphore.withPermit {
          fail()
        }
      }
      for (job in jobs) {
        assertTrue(job.isActive)
        assertFalse(job.isCompleted)
        assertFalse(job.isCancelled)
        job.cancel()
      }
    }
  }

  private fun CoroutineScope.fillQueue(semaphore: OverflowSemaphore, concurrency: Int): List<Job> {
    return (1..concurrency).map {
      launch(start = CoroutineStart.UNDISPATCHED) {
        semaphore.withPermit {
          awaitCancellation()
        }
      }
    }
  }
}
