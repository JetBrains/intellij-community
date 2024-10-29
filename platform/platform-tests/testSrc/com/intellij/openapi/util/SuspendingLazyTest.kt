// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.application.impl.assertReferenced
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.LazyRecursionPreventedException
import com.intellij.util.SuspendingLazy
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

@TestApplication
class SuspendingLazyTest {

  @Test
  fun `initializer is executed once and returns`(): Unit = timeoutRunBlocking(60.seconds) {
    val result = Any()
    val executed = AtomicBoolean(false)
    val initializer: suspend CoroutineScope.() -> Any = {
      assertFalse(executed.getAndSet(true))
      result
    }
    val lazy = suspendingLazy(initializer = initializer)
    assertUninitialized(lazy)
    assertFalse(executed.get())
    assertReferenced(lazy, initializer)

    assertInitialized(lazy, result)
    assertTrue(executed.get())
    LeakHunter.checkLeak(lazy, initializer::class.java)

    assertInitialized(lazy, result)
    assertTrue(executed.get())
    LeakHunter.checkLeak(lazy, initializer::class.java)
  }

  @Test
  fun `initializer is executed once and throws`(): Unit = timeoutRunBlocking(60.seconds) {
    testRethrow(object : Throwable() {})
  }

  @Test
  fun `initializer is executed once and throws CE`(): Unit = timeoutRunBlocking(60.seconds) {
    testRethrow(CancellationException())
  }

  private suspend fun testRethrow(t: Throwable): Unit = coroutineScope {
    val executed = AtomicBoolean(false)
    val initializer: suspend CoroutineScope.() -> Nothing = {
      assertFalse(executed.getAndSet(true))
      throw t
    }
    val lazy = suspendingLazy(initializer = initializer)
    assertUninitialized(lazy)
    assertFalse(executed.get())
    assertReferenced(lazy, initializer)

    assertFailed(lazy, t)
    assertTrue(executed.get())
    LeakHunter.checkLeak(lazy, initializer::class.java)

    assertFailed(lazy, t)
  }

  @Test
  fun `multiple waiters`(): Unit = timeoutRunBlocking {
    val result = Any()
    val executed = AtomicBoolean(false)
    val lazy = suspendingLazy {
      assertFalse(executed.getAndSet(true))
      result
    }
    repeat(5) {
      launch(start = CoroutineStart.UNDISPATCHED) { // execute until first suspension
        assertSame(result, lazy.getValue())
      }
    }
  }

  @Test
  fun `cancellation of waiters cancels initializer`(): Unit = timeoutRunBlocking {
    val result = Any()
    val executed = AtomicInteger(0)
    val started = Semaphore(1, 1)
    val lazy = suspendingLazy {
      when (executed.incrementAndGet()) {
        1 -> {
          started.release()
          awaitCancellation()
        }
        2 -> {
          result
        }
        else -> {
          fail("executed more than 2 times")
        }
      }
    }
    val firstTry = launch {
      repeat(5) {
        launch(start = CoroutineStart.UNDISPATCHED) {
          assertSame(result, lazy.getValue())
        }
      }
    }
    started.acquire()
    firstTry.cancel()
    yield()

    assertUninitialized(lazy)
    assertEquals(1, executed.get())

    assertInitialized(lazy, result)
    assertEquals(2, executed.get())
  }

  @RepeatedTest(100)
  fun `cancellation stress test`(): Unit = timeoutRunBlocking {
    val canFinish = Semaphore(1, 1)
    val result = Any()
    val lazy = suspendingLazy {
      canFinish.acquire()
      result
    }
    withContext(Dispatchers.Default) {
      repeat(1000) {
        val waiter = launch {
          delay(1)
          lazy.getValue()
        }
        launch {
          delay(1)
          waiter.cancel()
        }
      }
    }
    assertUninitialized(lazy)
    canFinish.release()
    assertInitialized(lazy, result)
  }

  @Test
  fun `cancellation of lazy scope before getValue`(): Unit = runBlocking {
    val cs = CoroutineScope(EmptyCoroutineContext)
    val initializer: suspend CoroutineScope.() -> Nothing = {
      fail("must not be executed")
    }
    val lazy = cs.suspendingLazy(initializer = initializer)
    val t: Throwable = object : Throwable() {}
    cs.cancel("cc", t)

    assertUninitialized(lazy)
    assertReferenced(lazy, initializer)
    assertCancelled(lazy, t)

    LeakHunter.checkLeak(lazy, initializer::class.java)
    assertCancelled(lazy, t)
  }

  @RepeatedTest(100)
  fun `cancellation of lazy scope during getValue`(): Unit = timeoutRunBlocking {
    val executed = AtomicBoolean(false)
    val cs = CoroutineScope(coroutineContext + Job())
    val lazy = cs.suspendingLazy {
      assertFalse(executed.getAndSet(true))
      awaitCancellation()
    }
    assertUninitialized(lazy)
    assertFalse(executed.get())

    val t: Throwable = object : Throwable() {}

    launch(start = CoroutineStart.UNDISPATCHED) {
      assertCancelled(lazy, t) // will suspend, and runBlocking will execute the cancelling coroutine
      assertTrue(executed.get())
      assertFalse(cs.isActive)
      assertCancelled(lazy, t) // will immediately resume with the same exception
    }

    launch { // cancelling coroutine
      assertUninitialized(lazy)
      assertTrue(executed.get())
      assertTrue(cs.isActive)
      cs.cancel("ce", t)
    }
  }

  @RepeatedTest(100)
  fun `cancellation of lazy scope and waiter during getValue`(): Unit = timeoutRunBlocking {
    val cs = CoroutineScope(coroutineContext + Job())
    val lazy = cs.suspendingLazy {
      awaitCancellation()
    }
    assertUninitialized(lazy)
    val waiter = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
      assertThrows<CancellationException> {
        lazy.getValue()
      }
    }
    launch(Dispatchers.Default) {
      waiter.cancel()
    }
    launch(Dispatchers.Default) {
      cs.cancel()
    }
  }

  @Test
  fun `cancellation of lazy scope after getValue`(): Unit = timeoutRunBlocking {
    val executed = AtomicBoolean(false)
    val result = Any()
    val cs = CoroutineScope(EmptyCoroutineContext)
    val lazy = cs.suspendingLazy {
      assertFalse(executed.getAndSet(true))
      result
    }
    assertUninitialized(lazy)
    assertFalse(executed.get())

    assertInitialized(lazy, result)
    assertTrue(executed.get())

    cs.cancel()

    assertInitialized(lazy, result)
    assertTrue(executed.get())
  }

  @RepeatedTest(100)
  fun `recursive lazy`(): Unit = timeoutRunBlocking {
    fun test(lazy: SuspendingLazy<Any>) = launch(Dispatchers.Default) {
      val caught = assertThrows<LazyRecursionPreventedException> { lazy.getValue() }
      assertTrue(lazy.isInitialized())
      assertTrue(caught.message!!.contains("lazy1name"))
      assertTrue(caught.message!!.contains("lazy2name"))
      assertTrue(caught.message!!.contains("lazy3name"))
      assertSame(caught, assertThrows<LazyRecursionPreventedException> { lazy.getValue() })
      assertSame(caught, assertThrows<LazyRecursionPreventedException> { lazy.getInitialized() })
    }

    lateinit var lazy2: SuspendingLazy<Int>
    lateinit var lazy3: SuspendingLazy<Int>
    val lazy1: SuspendingLazy<Int> = suspendingLazy(CoroutineName("lazy1name")) {
      lazy2.getValue()
    }
    lazy2 = suspendingLazy(CoroutineName("lazy2name")) {
      lazy3.getValue()
    }
    lazy3 = suspendingLazy(CoroutineName("lazy3name")) {
      lazy1.getValue()
    }

    test(lazy1)
    test(lazy2)
    test(lazy3)
  }

  @Test
  fun `recursive lazy via blockingContext and runBlockingCancellable`(): Unit = timeoutRunBlocking {
    lateinit var lazy2: SuspendingLazy<Int>
    val lazy1: SuspendingLazy<Int> = suspendingLazy(CoroutineName("lazy1name")) {
      blockingContext {
        runBlockingCancellable {
          lazy2.getValue()
        }
      }
    }
    lazy2 = suspendingLazy(CoroutineName("lazy2name")) {
      blockingContext {
        runBlockingCancellable {
          lazy1.getValue()
        }
      }
    }
    assertThrows<LazyRecursionPreventedException> {
      blockingContext {
        runBlockingCancellable {
          lazy1.getValue()
        }
      }
    }
  }
}

private fun assertUninitialized(lazy: SuspendingLazy<*>) {
  assertFalse(lazy.isInitialized())
  assertThrows<IllegalStateException> { lazy.getInitialized() }
}

private suspend fun <T> assertInitialized(lazy: SuspendingLazy<T>, value: T) {
  assertSame(value, lazy.getValue()) // may trigger computation
  assertTrue(lazy.isInitialized())
  assertSame(value, lazy.getInitialized())
}

private suspend fun assertFailed(lazy: SuspendingLazy<*>, t: Throwable) {
  assertSame(t, assertThrows { lazy.getValue() }) // may trigger computation
  assertTrue(lazy.isInitialized())
  assertSame(t, assertThrows { lazy.getInitialized() })
}

private suspend fun assertCancelled(lazy: SuspendingLazy<*>, t: Throwable) {
  assertSame(t, assertThrows<CancellationException> { lazy.getValue() }.cause) // may trigger computation
  assertTrue(lazy.isInitialized())
  assertSame(t, assertThrows<CancellationException> { lazy.getInitialized() }.cause)
}
