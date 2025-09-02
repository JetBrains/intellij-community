// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.fus.EdtFreezeChainMonitor
import com.intellij.openapi.application.*
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class EdtFreezeChainMonitorTest {
  @Volatile
  private var service: EdtFreezeChainMonitor? = null

  @Volatile
  private var scope: CoroutineScope? = null

  @BeforeEach
  fun setup() {
    @Suppress("RAW_SCOPE_CREATION")
    scope = CoroutineScope(Job())
    service = EdtFreezeChainMonitor(scope!!)
    // set testing overrides to ensure deterministic thresholds regardless of service init timing
    EdtFreezeChainMonitor.testingConfig =
      EdtFreezeChainMonitor.TestingConfig(windowMs = 100, thresholdMs = 20, reportableMs = 200)
  }

  @AfterEach
  fun tearDown() {
    // reset any histograms to avoid interference between tests
    EdtFreezeChainMonitor.testingConfig = null
    scope!!.cancel()
    scope = null
    service = null
  }

  fun doTest(uiAction: suspend () -> Unit, validator: (List<EdtFreezeChainMonitor.FreezeChainReport>) -> Boolean) = timeoutRunBlocking(context = Dispatchers.Default) {
    delay(200)
    EdtFreezeChainMonitor.testingConfig!!.drainReports()
    withContext(Dispatchers.UiWithModelAccess) {
      uiAction()
    }
    // end the sliding window
    withContext(Dispatchers.UI) { delay(150.milliseconds) }

    val reports = EdtFreezeChainMonitor.testingConfig!!.drainReports()
    assertTrue {
      validator(reports)
    }
  }

  @Test
  fun `long write action is reported`(): Unit = doTest(
    {
      runWriteAction {
        Thread.sleep(220)
      }
    }, { list ->
      list.any {
        it.durationNs >= 220.milliseconds.inWholeNanoseconds
        && it.totalWriteOps > 0
        && it.totalWriteNs >= 220.milliseconds.inWholeNanoseconds
      }
    })

  @Test
  fun `long read action is reported`(): Unit = doTest(
    {
      runReadAction {
        Thread.sleep(220)
      }
    }, { list ->
      list.any {
        it.durationNs >= 220.milliseconds.inWholeNanoseconds
        && it.totalReadOps > 0
        && it.totalReadNs >= 220.milliseconds.inWholeNanoseconds
      }
    })

  @Test
  fun `consecutive actions are reported`(): Unit = doTest(
    {
      runReadAction {
        Thread.sleep(110)
      }
      runWriteAction {
        Thread.sleep(110)
      }
    }, { list ->
      list.any {
        it.durationNs >= 220.milliseconds.inWholeNanoseconds
        && it.totalReadOps > 0
        && it.totalReadNs >= 100.milliseconds.inWholeNanoseconds
        && it.totalWriteOps > 0
        && it.totalWriteNs >= 100.milliseconds.inWholeNanoseconds
      }
    })

  @Test
  fun `consecutive actions with pauses are reported`(): Unit = doTest(
    {
      runReadAction {
        Thread.sleep(110)
      }
      delay(50.milliseconds)
      runWriteAction {
        Thread.sleep(110)
      }
    }, { list ->
      list.any {
        it.durationNs >= 270.milliseconds.inWholeNanoseconds
        && it.totalReadOps > 0
        && it.totalReadNs >= 100.milliseconds.inWholeNanoseconds
        && it.totalWriteOps > 0
        && it.totalWriteNs >= 100.milliseconds.inWholeNanoseconds
      }
    })

  @Test
  fun `consecutive actions with large pauses are reported as different events`(): Unit = doTest(
    {
      runReadAction {
        Thread.sleep(110)
      }
      delay(200.milliseconds)
      runWriteAction {
        Thread.sleep(110)
      }
    }, { list ->
      list.size >= 2
      && list.any {
        it.durationNs >= 110.milliseconds.inWholeNanoseconds
        && it.totalReadOps > 0
        && it.totalReadNs >= 100.milliseconds.inWholeNanoseconds
        && it.totalWriteOps == 0
      }
      && list.any {
        it.durationNs >= 110.milliseconds.inWholeNanoseconds
        && it.totalWriteOps > 0
        && it.totalWriteNs >= 100.milliseconds.inWholeNanoseconds
        && it.totalReadNs <= 10.milliseconds.inWholeNanoseconds
      }
    })

  @Test
  fun `many small actions are not reported`(): Unit = doTest(
    {
      runReadAction {
        Thread.sleep(10)
      }
      delay(200.milliseconds)
      runWriteAction {
        Thread.sleep(10)
      }
    }, { list ->
      list.isEmpty()
    })

  @Test
  fun `small actions with small delay are reported`(): Unit = doTest(
    {
      repeat(10) {
        runReadAction {
          Thread.sleep(11)
        }
        delay(30.milliseconds)
      }
    }, { list ->
      list.any {
        it.totalReadNs >= 99.milliseconds.inWholeNanoseconds
        && it.totalReadOps >= 10
        && it.durationNs >= 400.milliseconds.inWholeNanoseconds
      }
    })

  @Test
  fun `modal computations do not contribute to chain duration`(): Unit = doTest(
    {
      WriteIntentReadAction.run {
        Thread.sleep(150)
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          Thread.sleep(300)
        }
        Thread.sleep(150)
      }
    }, { list ->
      list.count {
        it.totalReadNs >= 140.milliseconds.inWholeNanoseconds
      } == 2
      &&
      list.all {
        it.durationNs < 300.milliseconds.inWholeNanoseconds
      }
    })
}
