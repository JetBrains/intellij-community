// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.virtualThreads.IntelliJVirtualThreads
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContains
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
      } catch (e : InterruptedException) {
        // expected
      }
    }
    Thread.sleep(10)
    assertTrue(thread.isAlive)
    thread.interrupt()
    thread.join()
    future.complete(Unit)
  }
}