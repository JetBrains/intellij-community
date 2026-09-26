// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import org.jetbrains.concurrency.Promise
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeActivityTest {

  class TestIdeActivity : IdeActivity {
    var finished = false

    override val id: Int = 1
    override val stepId: Int = 1
    override val startedTimestamp: Long = 1L
    override fun isFinished(): Boolean = finished
    override fun started(): IdeActivity = started(null)
    override fun started(dataSupplier: (() -> List<EventPair<*>>)?): IdeActivity = this
    override fun startedAsync(dataSupplier: () -> Promise<List<EventPair<*>>>): IdeActivity = this
    override fun stageStarted(stage: VarargEventId): IdeActivity = stageStarted(stage, null)
    override fun stageStarted(
      stage: VarargEventId,
      dataSupplier: (() -> List<EventPair<*>>)?,
    ): IdeActivity = this
    override fun finished(): IdeActivity = finished(null)
    override fun finished(dataSupplier: (() -> List<EventPair<*>>)?): IdeActivity {
      finished = true
      return this
    }
  }

  @Test
  fun testAutoCloseableActivity() {
    val testIdeActivity = TestIdeActivity()
    AutoClosableIdeActivity(testIdeActivity) {
      testIdeActivity.finished()
    }.use {
      assertFalse(testIdeActivity.isFinished())
    }

    assertTrue(testIdeActivity.isFinished())
  }

}
