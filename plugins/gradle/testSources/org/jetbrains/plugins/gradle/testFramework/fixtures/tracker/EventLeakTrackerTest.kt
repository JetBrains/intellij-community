// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventLeakTrackerTest {

  @Test
  fun `setUp and tearDown without events`() {
    val tracker = EventLeakTracker("test")
    tracker.setUp()
    tracker.tearDown()
  }

  @Test
  fun `event is allowed inside withAllowedOperationEvents`() {
    val tracker = EventLeakTracker("test")
    tracker.setUp()
    tracker.withAllowedOperationEvents {
      tracker.assertEventIsAllowed("MY_EVENT")
    }
    tracker.tearDown()
  }

  @Test
  fun `withAllowedOperationEvents returns action result`() {
    val tracker = EventLeakTracker("test")
    tracker.setUp()
    val result = tracker.withAllowedOperationEvents { 42 }
    assertEquals(42, result)
    tracker.tearDown()
  }

  @Test
  fun `event outside allowed window throws`() {
    val tracker = EventLeakTracker("test")
    tracker.setUp()
    assertThrows(AssertionError::class.java) {
      tracker.assertEventIsAllowed("MY_EVENT")
    }
    tracker.tearDown()
  }

  @Test
  fun `event is forbidden after withAllowedOperationEvents block ends`() {
    val tracker = EventLeakTracker("test")
    tracker.setUp()
    tracker.withAllowedOperationEvents { }
    assertThrows(AssertionError::class.java) {
      tracker.assertEventIsAllowed("MY_EVENT")
    }
    tracker.tearDown()
  }
}
