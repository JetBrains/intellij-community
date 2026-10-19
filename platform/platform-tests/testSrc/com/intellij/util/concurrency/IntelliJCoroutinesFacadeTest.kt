// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.IntelliJCoroutinesFacade.withGrantedParallelism
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.Ignore
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream
import kotlin.streams.toList
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalCoroutinesApi::class)
@TestApplication
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
class IntelliJCoroutinesFacadeTest {
  @Test
  //todo restore after fix
  @Disabled("hangs")
  fun launchingZillionOfCoroutinesMustNotPrecludeRunningAnotherInsideCompensateParallelism(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val latch = CountDownLatch(1)
    val poolSize = kotlinx.coroutines.scheduling.CORE_POOL_SIZE
    val started = AtomicInteger()
    val jobs = IntStream.range(0, poolSize).toList().map {
      launch(Dispatchers.Default) {
        started.incrementAndGet()
        latch.await()
      }
    }
    while (started.get() < poolSize) {
      Thread.yield()
    }
    val j2: AtomicReference<Job> = AtomicReference()
    Dispatchers.Default.withGrantedParallelism {
      j2.set(launch(Dispatchers.Default) {
          started.incrementAndGet()
      })
    }
    j2.get().join()
    Dispatchers.Default.withGrantedParallelism {
      val factory = IntelliJVirtualThreads.ofVirtual().name("IntelliJCoroutinesFacadeTest").factory()
      val thread = factory.newThread {}
      thread.start()
      thread.join()
    }
    latch.countDown()
    jobs.joinAll()
  }
}