// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads
import com.intellij.concurrency.virtualThreads.asyncAsVirtualThread
import com.intellij.concurrency.virtualThreads.inVirtualThread
import com.intellij.concurrency.virtualThreads.launchAsVirtualThread
import com.intellij.concurrency.virtualThreads.virtualThread
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

@TestApplication
class VirtualThreadTest {

  @Test
  fun basic() {
    val exceptionRef: AtomicReference<Throwable?> = AtomicReference()
    IntelliJVirtualThreads.ofVirtual().start {
      try {
        assertContains(Thread.currentThread().toString(), "DefaultDispatcher")
      } catch (e: Throwable) {
        exceptionRef.set(e)
      }
    }.join()
    exceptionRef.get()?.let { throw it }
  }

  @Test
  fun cancellation(): Unit = timeoutRunBlocking {
    val future = Job(coroutineContext.job).asCompletableFuture()
    val thread = IntelliJVirtualThreads.ofVirtual().start {
      try {
        future.get()
        fail("must not be reached")
      } catch (_ : InterruptedException) {
        // expected
      }
    }
    Thread.sleep(10)
    assertTrue(thread.isAlive)
    thread.interrupt()
    thread.join()
    future.complete(Unit)
  }

  @Test
  fun `kotlin thread builder uses intellij builder under the hood`() {
    val exceptionRef: AtomicReference<Throwable?> = AtomicReference()
    virtualThread {
      try {
        assertContains(Thread.currentThread().toString(), "DefaultDispatcher")
      } catch (e: Throwable) {
        exceptionRef.set(e)
      }
    }.join()
    exceptionRef.get()?.let { throw it }
  }


  @Test
  fun `start false returns unstarted thread which can be started manually`() {
    val latch = CountDownLatch(1)
    val t = virtualThread(start = false) {
      latch.countDown()
    }
    assertFalse(t.isAlive, "thread must not be started when start=false")
    // start manually
    t.start()
    assertTrue(latch.await(1, TimeUnit.SECONDS), "block should run after starting the thread manually")
  }

  @Test
  fun `thread is created with given name`() {
    val latch = CountDownLatch(1)
    val threadName = "My-VT-Test"
    var seenName: String? = null
    val t = virtualThread(name = threadName) {
      seenName = Thread.currentThread().name
      latch.countDown()
    }
    t.join()
    assertTrue(latch.await(0, TimeUnit.SECONDS))
    assertEquals(threadName, seenName)
  }

  @Test
  fun `context class loader is applied`() {
    // Use a non-null dummy classloader wrapper to ensure setter is used
    val customCl = object : ClassLoader(this::class.java.classLoader) {}
    var seenCl: ClassLoader? = null
    val t = virtualThread(contextClassLoader = customCl) {
      seenCl = Thread.currentThread().contextClassLoader
    }
    t.join()
    assertNotNull(seenCl)
    assertEquals(customCl, seenCl)
  }

  private data class DummyElement(val name: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<DummyElement>
    override val key: CoroutineContext.Key<*> = Key
  }

  @Test
  fun `context is propagated into virtual thread and factory-spawned threads`() = timeoutRunBlocking {
    val name = "TestVT-Context"

    val computationResult = withContext(DummyElement(name)) {
      asyncAsVirtualThread {
        val element = requireNotNull(currentThreadContext()[DummyElement.Key])
        assertEquals(name, element.name)
        "ok"
      }.await()
    }

    assertEquals("ok", computationResult)
  }

  @Test
  fun `exception in action completes deferred exceptionally`(): Unit = timeoutRunBlocking {
    val ex = RuntimeException("boom")
    supervisorScope {
      val d: Deferred<Unit> = asyncAsVirtualThread {
        throw ex
      }
      try {
        d.await()
        fail<Nothing>("must not be reached")
      } catch (e: RuntimeException) {
        assertEquals(ex, e.cause)
      }
    }
  }

  @Test
  fun `cancellation of virtual threads interrupts it`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val eternalSuspension = CompletableFuture<Unit>()
    val d = asyncAsVirtualThread {
      eternalSuspension.get()
    }
    delay(100)
    d.cancelAndJoin()
  }

  @Test
  fun `lazy start delays thread creation until awaited or started`() = timeoutRunBlocking(context = Dispatchers.Default) {
    var executed = false
    val d = asyncAsVirtualThread(start = CoroutineStart.LAZY) {
      executed = true
      "res"
    }
    assertFalse(d.isActive)
    assertFalse(executed)
    assertEquals("res", d.await())
    assertTrue(executed)
  }

  @Test
  fun `launchAsVirtualThread runs on virtual thread`(): Unit = timeoutRunBlocking {
    launchAsVirtualThread {
      assertContains(Thread.currentThread().toString(), "DefaultDispatcher")
    }.join()
  }

  @Test
  fun `launchAsVirtualThread does not run on context dispatcher`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      launchAsVirtualThread {
        assertFalse(EDT.isCurrentThreadEdt())
      }.join()
    }
  }

  @Test
  fun `cannot launch virtual thread coroutine in UNDISPATHCED`(): Unit = timeoutRunBlocking {
    assertThrows<IllegalArgumentException> {
      launchAsVirtualThread(start = CoroutineStart.UNDISPATCHED) {
      }.join()
    }
  }

  @Test
  fun `stacktraces of virtual threads get into coroutine dump`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val eternalSuspension = CompletableFuture<Unit>()
    asyncAsVirtualThread {
      interestingStackTrace {
        eternalSuspension.get()
      }
    }
    delay(100)
    val dump = dumpCoroutines(this)!!
    assertContains(dump, "interestingStackTrace")
    eternalSuspension.complete(Unit)
  }

  fun interestingStackTrace(action: () -> Unit) {
    action()
  }

  @Test
  fun `inVirtualThread runs computations in a virtual thread`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val x = inVirtualThread {
      assertContains(Thread.currentThread().toString(), "DefaultDispatcher")
      assertContains(Thread.currentThread().toString(), "VirtualThread")
      42
    }
    assertEquals(42, x)
  }
}