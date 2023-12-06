// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse

@TestApplication
class SpuriousPendingWATest {
  @Test
  fun testNoSpuriousWAOnBackgroundThreadProbabilistic() {
    val repetitions = 1000000;
    val app = ApplicationManagerEx.getApplicationEx()
    val start = Semaphore(1, 1)
    val finished = AtomicBoolean(false)

    val job = GlobalScope.namedChildScope("Bad WAs", Dispatchers.IO).async {
      start.acquire()
      for (i in 1..repetitions) {
        try {
          app.runWriteAction {}
        }
        catch (_: IllegalStateException) {
        }
      }
      finished.set(true)
    }

    val seen = runInEdtAndGet {
      start.release()
      while (!finished.get()) {
        if (app.isWriteActionPending) {
          return@runInEdtAndGet true
        }
      }
      return@runInEdtAndGet false
    }

    runBlocking {
      job.join()
    }

    assertFalse(seen)
  }
}