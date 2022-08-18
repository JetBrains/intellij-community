// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.application.impl.assertReferenced
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.timeoutRunBlocking
import com.intellij.testFramework.LeakHunter
import com.intellij.util.LazyRecursionPreventedException
import com.intellij.util.SuspendingLazy
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext

class SuspendingLazyTest {

  @Test
  fun `initializer is executed once and returns`(): Unit = timeoutRunBlocking {
    val result = Any()
    val executed = AtomicBoolean(false)
    val initializer: suspend CoroutineScope.() -> Any = {
      Assertions.assertFalse(executed.getAndSet(true))
      result
    }
    val lazy = suspendingLazy(initializer = initializer)
    Assertions.assertFalse(lazy.isInitialized())
    Assertions.assertFalse(executed.get())
    assertReferenced(lazy, initializer)

    Assertions.assertSame(result, lazy.getValue())
    Assertions.assertTrue(lazy.isInitialized())
    Assertions.assertTrue(executed.get())
    LeakHunter.checkLeak(lazy, initializer::class.java)

    Assertions.assertSame(result, lazy.getValue())
    Assertions.assertTrue(lazy.isInitialized())
    Assertions.assertTrue(executed.get())
    LeakHunter.checkLeak(lazy, initializer::class.java)
  }

  @Test
  fun `initializer is executed once and throws`(): Unit = timeoutRunBlocking {
    testRethrow(object : Throwable() {})
  }

  @Test
  fun `initializer is executed once and throws CE`(): Unit = timeoutRunBlocking {
    testRethrow(CancellationException())
  }

  private suspend fun testRethrow(t: Throwable): Unit = coroutineScope {
    val executed = AtomicBoolean(false)
    val initializer: suspend CoroutineScope.() -> Nothing = {
      Assertions.assertFalse(executed.getAndSet(true))
      throw t
    }
    val lazy = suspendingLazy(initializer = initializer)
    Assertions.assertFalse(lazy.isInitialized())
    Assertions.assertFalse(executed.get())
    assertReferenced(lazy, initializer)

    Assertions.assertSame(t, assertThrows { lazy.getValue() })
    Assertions.assertTrue(lazy.isInitialized())
    Assertions.assertTrue(executed.get())
    LeakHunter.checkLeak(lazy, initializer::class.java)

    Assertions.assertSame(t, assertThrows { lazy.getValue() })
  }

  @Test
  fun `multiple waiters`(): Unit = timeoutRunBlocking {
    val result = Any()
    val executed = AtomicBoolean(false)
    val lazy = suspendingLazy {
      Assertions.assertFalse(executed.getAndSet(true))
      result
    }
    repeat(5) {
      launch(start = CoroutineStart.UNDISPATCHED) { // execute until first suspension
        Assertions.assertSame(result, lazy.getValue())
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
          Assertions.assertSame(result, lazy.getValue())
        }
      }
    }
    started.acquire()
    firstTry.cancel()
    yield()

    Assertions.assertFalse(lazy.isInitialized())
    Assertions.assertEquals(1, executed.get())

    Assertions.assertSame(result, lazy.getValue())
    Assertions.assertTrue(lazy.isInitialized())
    Assertions.assertEquals(2, executed.get())
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
    Assertions.assertFalse(lazy.isInitialized())
    canFinish.release()
    Assertions.assertSame(result, lazy.getValue())
    Assertions.assertTrue(lazy.isInitialized())
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

    Assertions.assertFalse(lazy.isInitialized())
    assertReferenced(lazy, initializer)
    testException(lazy, t)

    Assertions.assertTrue(lazy.isInitialized())
    LeakHunter.checkLeak(lazy, initializer::class.java)
    testException(lazy, t)
  }

  @RepeatedTest(100)
  fun `cancellation of lazy scope during getValue`(): Unit = timeoutRunBlocking {
    val executed = AtomicBoolean(false)
    val cs = CoroutineScope(coroutineContext + Job())
    val lazy = cs.suspendingLazy {
      Assertions.assertFalse(executed.getAndSet(true))
      awaitCancellation()
    }
    Assertions.assertFalse(lazy.isInitialized())
    Assertions.assertFalse(executed.get())

    val t: Throwable = object : Throwable() {}

    launch(start = CoroutineStart.UNDISPATCHED) {
      testException(lazy, t) // will suspend, and runBlocking will execute the cancelling coroutine
      Assertions.assertTrue(lazy.isInitialized())
      Assertions.assertTrue(executed.get())
      Assertions.assertFalse(cs.isActive)
      testException(lazy, t) // will immediately resume with the same exception
    }

    launch { // cancelling coroutine
      Assertions.assertFalse(lazy.isInitialized())
      Assertions.assertTrue(executed.get())
      Assertions.assertTrue(cs.isActive)
      cs.cancel("ce", t)
    }
  }

  @RepeatedTest(100)
  fun `cancellation of lazy scope and waiter during getValue`(): Unit = timeoutRunBlocking {
    val cs = CoroutineScope(coroutineContext + Job())
    val lazy = cs.suspendingLazy {
      awaitCancellation()
    }
    Assertions.assertFalse(lazy.isInitialized())
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
      Assertions.assertFalse(executed.getAndSet(true))
      result
    }
    Assertions.assertFalse(lazy.isInitialized())
    Assertions.assertFalse(executed.get())

    Assertions.assertSame(result, lazy.getValue())
    Assertions.assertTrue(lazy.isInitialized())
    Assertions.assertTrue(executed.get())

    cs.cancel()

    Assertions.assertSame(result, lazy.getValue())
    Assertions.assertTrue(lazy.isInitialized())
    Assertions.assertTrue(executed.get())
  }

  private suspend fun testException(lazy: SuspendingLazy<Unit>, t: Throwable) {
    val thrown: CancellationException = assertThrows {
      lazy.getValue()
    }
    Assertions.assertSame(t, thrown.cause)
  }

  @RepeatedTest(100)
  fun `recursive lazy`(): Unit = timeoutRunBlocking {
    fun test(lazy: SuspendingLazy<Any>) = launch(Dispatchers.Default) {
      val caught = assertThrows<LazyRecursionPreventedException> { lazy.getValue() }
      Assertions.assertTrue(lazy.isInitialized())
      Assertions.assertTrue(caught.message!!.contains("lazy1name"))
      Assertions.assertTrue(caught.message!!.contains("lazy2name"))
      Assertions.assertTrue(caught.message!!.contains("lazy3name"))
      Assertions.assertSame(caught, assertThrows<LazyRecursionPreventedException> { lazy.getValue() })
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
