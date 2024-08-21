// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestApplication
class AnyThreadWriteThreadingSupportTest {
  @RepeatedTest(1000)
  fun testInterruptedOrLockAcquired() = timeoutRunBlocking(2.seconds) {
    val lock = IdeEventQueue.getInstance().threadingSupport

    val readRun = AtomicBoolean(false)
    val readInterrupted = AtomicBoolean(false)
    val readThreadStarted = Semaphore(1, 1)
    val readThreadEnded = Semaphore(1, 1)
    lock.runWriteAction {
      // Run background read action, it should block as we are in write action
      val rt = Thread({
        try {
          readThreadStarted.release()
          lock.runReadAction {
            readRun.set(true)
          }
        } catch (_: InterruptedException) {
          readInterrupted.set(true)
        } finally {
          readThreadEnded.release()
        }
      }, "Read Action")
      rt.isDaemon = true
      rt.start()
      timeoutRunBlocking {
        readThreadStarted.acquire()
      }
      // Maybe in lock, maybe earlier
      rt.interrupt()
    }
    readThreadEnded.acquire()

    // Test that write action is Ok now, no lock leaked
    val secondWriteRun = AtomicBoolean(false)
    lock.runWriteAction {
      secondWriteRun.set(true)
    }
    assertTrue(readRun.get() != readInterrupted.get())
    assertTrue(secondWriteRun.get())
  }
}
