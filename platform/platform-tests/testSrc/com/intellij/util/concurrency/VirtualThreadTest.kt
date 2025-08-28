// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.virtualThreads.IntelliJVirtualThreads
import com.intellij.virtualThreads.virtualThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

}