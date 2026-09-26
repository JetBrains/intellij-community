// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performanceTests

import com.intellij.internal.performanceTests.ProjectInitializationDiagnostic.ActivityTracker
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

internal class AggregatedActivityTrackerTest {

  @Test
  fun `activityFinished delegates to all trackers`() {
    val events = mutableListOf<String>()
    val tracker = AggregatedActivityTracker(listOf(RecordingActivityTracker("first", events),
                                                   RecordingActivityTracker("second", events),
                                                   RecordingActivityTracker("third", events)))

    tracker.activityFinished()

    assertContentEquals(listOf("first", "second", "third"), events)
  }

  @Test
  fun `activityFinished delegates to all trackers and rethrows collected exceptions`() {
    val events = mutableListOf<String>()
    val firstException = IllegalStateException("first")
    val secondException = IllegalArgumentException("second")
    val tracker = AggregatedActivityTracker(listOf(RecordingActivityTracker("first", events),
                                                   RecordingActivityTracker("failing-first", events, firstException),
                                                   RecordingActivityTracker("middle", events),
                                                   RecordingActivityTracker("failing-second", events, secondException),
                                                   RecordingActivityTracker("last", events)))

    val thrown = assertFailsWith<IllegalStateException> {
      tracker.activityFinished()
    }

    assertSame(firstException, thrown)
    assertContentEquals(arrayOf(secondException), thrown.suppressed)
    assertContentEquals(listOf("first", "failing-first", "middle", "failing-second", "last"), events)
  }

  private class RecordingActivityTracker(
    private val name: String,
    private val events: MutableList<String>,
    private val exception: Throwable? = null,
  ) : ActivityTracker {
    override fun activityFinished() {
      events.add(name)
      exception?.let { throw it }
    }
  }
}
