// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream
import kotlin.streams.asStream

@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrentCollectionProcessingTest {

  @ParameterizedTest
  @ValueSource(ints = [-1, 0])
  fun incorrectConcurrency(concurrency: Int): Unit = timeoutRunBlocking {
    val items = List(10) { it }
    assertThrows<IllegalArgumentException> {
      items.forEachConcurrent(concurrency) {
        fail("unreachable")
      }
    }
    assertThrows<IllegalArgumentException> {
      items.transformConcurrent<_, Nothing>(concurrency) {
        fail("unreachable")
      }
    }
    assertThrows<IllegalArgumentException> {
      items.mapConcurrent(concurrency) {
        fail("unreachable")
      }
    }
    assertThrows<IllegalArgumentException> {
      items.filterConcurrent(concurrency) {
        fail("unreachable")
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [
    1,  // concurrency == 1 (sequential branch)
    16, // 1 < concurrency < workload (channel branch)
    64, // concurrency >= workload (coroutine per item branch)
  ])
  fun rethrow(concurrency: Int): Unit = timeoutRunBlocking {
    val t = object : Throwable() {}
    val items = List(32) { it }.toSet()

    assertThrows<Throwable> {
      items.forEachConcurrent(concurrency) {
        throw t
      }
    }.let {
      assertSame(t, it)
    }
    assertFalse(currentCoroutineContext().job.isCancelled)

    assertThrows<Throwable> {
      items.transformConcurrent<_, Nothing>(concurrency) {
        throw t
      }
    }.let {
      assertSame(t, it)
    }
    assertFalse(currentCoroutineContext().job.isCancelled)

    assertThrows<Throwable> {
      items.mapConcurrent(concurrency) {
        throw t
      }
    }.let {
      assertSame(t, it)
    }
    assertFalse(currentCoroutineContext().job.isCancelled)

    assertThrows<Throwable> {
      items.filterConcurrent(concurrency) {
        throw t
      }
    }.let {
      assertSame(t, it)
    }
    assertFalse(currentCoroutineContext().job.isCancelled)
  }

  @ParameterizedTest
  @ArgumentsSource(ConcurrencyArguments::class)
  fun forEachConcurrent(concurrency: Int, workload: Int, parallelism: Int): Unit = timeoutRunBlocking {
    val items = List(workload) { it }.toSet()
    val concurrent = AtomicInteger()
    val processed: MutableSet<Int> = ConcurrentCollectionFactory.createConcurrentSet()
    withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
      items.forEachConcurrent(concurrency) {
        assertTrue(concurrent.incrementAndGet() <= concurrency)
        randomYield()
        assertTrue(processed.add(it))
        assertTrue(concurrent.getAndDecrement() > 0)
      }
    }
    assertEquals(items.size, processed.size)
    assertEquals(items, processed)
  }

  @ParameterizedTest
  @ArgumentsSource(ConcurrencyArguments::class)
  fun transformConcurrent(concurrency: Int, workload: Int, parallelism: Int): Unit = timeoutRunBlocking {
    val items = List(workload) { it }.toSet()
    val concurrent = AtomicInteger()
    val processed: MutableSet<Int> = ConcurrentCollectionFactory.createConcurrentSet()
    val result: Collection<Int> = withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
      items.transformConcurrent(concurrency) {
        assertTrue(concurrent.incrementAndGet() <= concurrency)
        randomYield()
        assertTrue(processed.add(it))
        out(it)
        assertTrue(concurrent.getAndDecrement() > 0)
      }
    }
    assertEquals(items.size, processed.size)
    assertEquals(items, processed)
    assertEquals(items.size, result.size)
    assertEquals(items, result.toSet())
  }

  @ParameterizedTest
  @ArgumentsSource(ConcurrencyArguments::class)
  fun mapConcurrent(concurrency: Int, workload: Int, parallelism: Int): Unit = timeoutRunBlocking {
    val items = List(workload) { it }.toSet()
    val concurrent = AtomicInteger()
    val processed: MutableSet<Int> = ConcurrentCollectionFactory.createConcurrentSet()
    val result: Collection<Int> = withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
      items.mapConcurrent(concurrency) {
        assertTrue(concurrent.incrementAndGet() <= concurrency)
        randomYield()
        assertTrue(processed.add(it))
        assertTrue(concurrent.getAndDecrement() > 0)
        it
      }
    }
    assertEquals(items.size, processed.size)
    assertEquals(items, processed)
    assertEquals(items.size, result.size)
    assertEquals(items, result.toSet())
  }

  @ParameterizedTest
  @ArgumentsSource(ConcurrencyArguments::class)
  fun filterConcurrent(concurrency: Int, workload: Int, parallelism: Int): Unit = timeoutRunBlocking {
    val items = List(workload) { it }.toSet()
    val concurrent = AtomicInteger()
    val processed: MutableSet<Int> = ConcurrentCollectionFactory.createConcurrentSet()
    val result: Collection<Int> = withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
      items.filterConcurrent(concurrency) {
        assertTrue(concurrent.incrementAndGet() <= concurrency)
        randomYield()
        assertTrue(processed.add(it))
        assertTrue(concurrent.getAndDecrement() > 0)
        true
      }
    }
    assertEquals(items.size, processed.size)
    assertEquals(items, processed)
    assertEquals(items.size, result.size)
    assertEquals(items, result.toSet())
  }

  private class ConcurrencyArguments : ArgumentsProvider {

    override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = sequence {
      // concurrency == 1 (sequential branch)
      for (workload in arrayOf(0, 1, 10, 100)) {
        for (parallelism in arrayOf(1, 8, 16, 32)) {
          yield(Arguments.of(1, workload, 1))
        }
      }

      // concurrency = 16
      arrayOf(
        0,      // corner case
        1,      // concurrency >= workload (coroutine per item branch)
        16,     // concurrency >= workload (coroutine per item branch)
        256,    // 1 < concurrency < workload (channel branch)
        65536,  // 1 < concurrency < workload (channel branch)
      ).forEach { workload ->
        arrayOf(
          1,  // sequential
          8,  // parallelism < concurrency
          16, // parallelism = concurrency
          32, // parallelism > concurrency
        ).forEach { parallelism ->
          yield(Arguments.of(16, workload, parallelism))
        }
      }
    }.asStream()
  }

  private suspend fun randomYield() {
    if (Math.random() >= 0.5) {
      yield()
    }
  }
}
