// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.jetbrains.kotlinx.lincheck.annotations.Operation
import org.jetbrains.kotlinx.lincheck.check
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Verifying the property of [DebouncedUpdates] to be tracked without gaps:
 *
 * Once [UpdateQueue.queue] is called and processing has not completed,
 * [UpdateQueue.isAllExecuted] must return `false`. This ensures that activity tracking
 * has no gaps — each internal phase's "active" state (channel non-empty → isCollecting → isProcessing)
 * overlaps with the next, so `isAllExecuted` never transiently returns `true`.
 */
class DebouncedUpdatesActivityCoverageLincheck {

  val queue: UpdateQueue<Unit> = DebouncedUpdates
    .forScope<Unit>(scope, "lincheck-coverage", Duration.ZERO)
    .runLatest { delay(Long.MAX_VALUE.milliseconds) }

  @Operation
  fun queue() = queue.queue(Unit)

  @Operation
  @Suppress("unused")
  fun isAllExecuted(): Boolean = queue.isAllExecuted

  @Test
  fun stressTest() = StressOptions()
    .sequentialSpecification(ActivityCoverageSeqSpec::class.java)
    .check(this::class)

  companion object {
    private lateinit var scope: CoroutineScope

    @JvmStatic
    @BeforeAll
    fun setUp() {
      @Suppress("RAW_SCOPE_CREATION")
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      scope.cancel()
    }
  }
}

class ActivityCoverageSeqSpec {
  private var hasPending = false
  fun queue() { hasPending = true }
  @Suppress("unused")
  fun isAllExecuted(): Boolean = !hasPending
}
