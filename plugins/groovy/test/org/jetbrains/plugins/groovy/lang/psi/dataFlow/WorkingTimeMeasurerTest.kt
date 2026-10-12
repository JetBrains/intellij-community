// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class WorkingTimeMeasurerTest {
  @Test
  fun timeoutExpiresOnVirtualThread() {
    assertTimeoutExpires(Thread.ofVirtual())
  }

  @Test
  fun timeoutExpiresOnPlatformThread() {
    assertTimeoutExpires(Thread.ofPlatform())
  }

  private fun assertTimeoutExpires(builder: Thread.Builder) {
    val task = FutureTask {
      val measurer = WorkingTimeMeasurer(TimeUnit.MILLISECONDS.toNanos(1))
      val started = System.nanoTime()
      while (!measurer.isTimeOver && System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5)) {
        Thread.onSpinWait()
      }
      measurer.isTimeOver
    }
    builder.start(task)
    assertTrue(task.get(10, TimeUnit.SECONDS))
  }
}
