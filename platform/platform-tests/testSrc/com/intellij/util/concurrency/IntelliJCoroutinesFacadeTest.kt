// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.IntelliJCoroutinesFacade.withGrantedParallelism
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
class IntelliJCoroutinesFacadeTest {
  @Test
  fun launchingZillionOfCoroutinesMustNotPrecludeRunningAnotherInsideCompensateParallelism(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val allowToExit = CountDownLatch(1)
    val poolSize = kotlinx.coroutines.scheduling.CORE_POOL_SIZE
    val started = CountDownLatch(poolSize)
    val jobs = List(poolSize) {
      launch(Dispatchers.Default) {
        started.countDown()
        allowToExit.await()
      }
    }
    try {
      assertTrue(started.await(10, SECONDS), "The coroutines did not saturate the Default dispatcher")
      Dispatchers.Default.withGrantedParallelism { granted ->
        assertTrue(granted, "The Default dispatcher did not grant extra parallelism")
        val ran = CountDownLatch(1)
        launch(Dispatchers.Default) {
          ran.countDown()
        }
        assertTrue(ran.await(10, SECONDS), "The coroutine did not run with extra parallelism")
      }
      Dispatchers.Default.withGrantedParallelism { granted ->
        assertTrue(granted, "The Default dispatcher did not grant extra parallelism")
        val factory = IntelliJVirtualThreads.ofVirtual().name("IntelliJCoroutinesFacadeTest").factory()
        val thread = factory.newThread {}
        thread.start()
        thread.join(10_000)
        assertFalse(thread.isAlive, "The virtual thread did not finish with extra parallelism")
      }
    }
    finally {
      allowToExit.countDown()
    }
    jobs.joinAll()
  }
}
